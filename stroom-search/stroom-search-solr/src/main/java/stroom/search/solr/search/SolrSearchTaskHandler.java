/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.search.solr.search;

import org.apache.solr.client.solrj.FastStreamingDocsCallback;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.StreamingResponseCallback;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.util.DataEntry;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValBoolean;
import stroom.dashboard.expression.v1.ValDouble;
import stroom.dashboard.expression.v1.ValErr;
import stroom.dashboard.expression.v1.ValInteger;
import stroom.dashboard.expression.v1.ValLong;
import stroom.dashboard.expression.v1.ValNull;
import stroom.dashboard.expression.v1.ValString;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.search.extraction.Values;
import stroom.search.solr.CachedSolrIndex;
import stroom.search.solr.SolrIndexClientCache;
import stroom.search.solr.search.SolrSearchTask.ResultReceiver;
import stroom.search.solr.shared.SolrConnectionConfig;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.search.solr.shared.SolrIndexField;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.shared.ThreadPool;
import stroom.task.shared.ThreadPoolImpl;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

public class SolrSearchTaskHandler {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SolrSearchTaskHandler.class);

    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl(
            "Search Solr Index Shard",
            5,
            0,
            Integer.MAX_VALUE);

    private final SolrIndexClientCache solrIndexClientCache;
    private final ExecutorProvider executorProvider;
    private final TaskContext taskContext;
    private final CountDownLatch completionLatch = new CountDownLatch(1);

    @Inject
    SolrSearchTaskHandler(final SolrIndexClientCache solrIndexClientCache,
                          final ExecutorProvider executorProvider,
                          final TaskContext taskContext) {
        this.solrIndexClientCache = solrIndexClientCache;
        this.executorProvider = executorProvider;
        this.taskContext = taskContext;
    }

    public VoidResult exec(final SolrSearchTask task) {
        LOGGER.logDurationIfDebugEnabled(
                () -> {
                    try {
                        taskContext.setName("Search Solr Index");
                        if (Thread.interrupted()) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Interrupted");
                        }

                        taskContext.info("Searching Solr index");

                        // Start searching.
                        searchShard(task);

                    } catch (final RuntimeException e) {
                        LOGGER.debug(e::getMessage, e);
                        error(task, e.getMessage(), e);
                    }
                },
                () -> "exec()");

        return VoidResult.INSTANCE;
    }

    private void searchShard(final SolrSearchTask task) {
        final CachedSolrIndex cachedSolrIndex = task.getSolrIndex();
        final SolrIndexDoc solrIndexDoc = cachedSolrIndex.getIndex();
        final SolrConnectionConfig connectionConfig = solrIndexDoc.getSolrConnectionConfig();

        // If there is an error building the query then it will be null here.
        try {
            final Executor executor = executorProvider.getExecutor(THREAD_POOL);
            CompletableFuture.runAsync(() -> {
                taskContext.setName("Index Searcher");
                LOGGER.logDurationIfDebugEnabled(
                        () -> {
//                            try {
//                                fastStreamingDocsSearch(task, solrIndex, connectionConfig);
//                            } catch (final RuntimeException e) {
//                                complete.set(true);
//                                error(task, e.getMessage(), e);
//                            }

                            try {
                                streamingSearch(task, solrIndexDoc, connectionConfig);
                            } catch (final RuntimeException e) {
                                error(task, e.getMessage(), e);
                            } finally {
                                task.getResultReceiver().receive(Values.COMPLETE);
                                // Send terminal values.
                                completionLatch.countDown();
                            }
                        },
                        () -> "searcher.search()");
            }, executor);
        } catch (final RuntimeException e) {
            error(task, e.getMessage(), e);
        }
    }

    private void fastStreamingDocsSearch(final SolrSearchTask task, final SolrIndexDoc solrIndexDoc, final SolrConnectionConfig connectionConfig) {
        final AtomicLong hitCount = new AtomicLong();
        final Callback2 callback = new Callback2(hitCount, task.getFieldNames(), task.getSolrIndex().getFieldsMap(), task.getResultReceiver(), task.getErrorReceiver());
        solrIndexClientCache.context(connectionConfig, solrClient -> {
            try {
                final QueryResponse response = solrClient.queryAndStreamResponse(solrIndexDoc.getCollection(), task.getSolrParams(), callback);
                LOGGER.debug(() -> "fastStreamingDocsSearch() - response=" + response);
            } catch (final SolrServerException | IOException | RuntimeException e) {
                error(task, e.getMessage(), e);
            }
        });
    }

    private void streamingSearch(final SolrSearchTask task, final SolrIndexDoc solrIndexDoc, final SolrConnectionConfig connectionConfig) {
        final AtomicLong hitCount = new AtomicLong();
        final Callback callback = new Callback(hitCount, task.getFieldNames(), task.getResultReceiver(), task.getErrorReceiver());
        solrIndexClientCache.context(connectionConfig, solrClient -> {
            try {
                final QueryResponse response = solrClient.queryAndStreamResponse(solrIndexDoc.getCollection(), task.getSolrParams(), callback);
                final DocListInfo docListInfo = callback.getDocListInfo();
                LOGGER.debug(() -> "streamingSearch() - response=" + response);
                LOGGER.debug(() -> "hitCount=" + hitCount.get());

                // Make sure we got back what we expected to.
                if (docListInfo == null) {
                    throw new SolrServerException("docListInfo is null");
                } else if (docListInfo.getNumFound() != hitCount.get()) {
                    throw new SolrServerException("Unexpected hit count - numFound=" + docListInfo.getNumFound() + " hitCount=" + hitCount.get());
                }

            } catch (final SolrServerException | IOException | RuntimeException e) {
                error(task, e.getMessage(), e);
            }
        });
    }

    private void error(final SolrSearchTask task, final String message, final Throwable t) {
        if (task == null) {
            LOGGER.error(() -> message, t);
        } else {
            task.getErrorReceiver().log(Severity.ERROR, null, null, message, t);
        }
    }

    public void awaitCompletion() throws InterruptedException {
        completionLatch.await();
    }

    private static class Callback extends StreamingResponseCallback {
        private final AtomicLong count;
        private final String[] fieldNames;
        private final ResultReceiver resultReceiver;
        private final ErrorReceiver errorReceiver;

        private DocListInfo docListInfo;

        Callback(final AtomicLong count,
                 final String[] fieldNames,
                 final ResultReceiver resultReceiver,
                 final ErrorReceiver errorReceiver) {
            this.count = count;
            this.fieldNames = fieldNames;
            this.resultReceiver = resultReceiver;
            this.errorReceiver = errorReceiver;
        }

        @Override
        public void streamSolrDocument(final SolrDocument doc) {
            try {
                count.incrementAndGet();

                Val[] values = null;
                for (int i = 0; i < fieldNames.length; i++) {
                    final String fieldName = fieldNames[i];
                    final Object object = doc.getFirstValue(fieldName);
                    if (object != null) {
                        if (values == null) {
                            values = new Val[fieldNames.length];
                        }

                        if (object instanceof Long) {
                            values[i] = ValLong.create((Long) object);
                        } else if (object instanceof Integer) {
                            values[i] = ValInteger.create((Integer) object);
                        } else if (object instanceof Double) {
                            values[i] = ValDouble.create((Double) object);
                        } else if (object instanceof Float) {
                            values[i] = ValDouble.create((Float) object);
                        } else if (object instanceof Boolean) {
                            values[i] = ValBoolean.create((Boolean) object);
                        } else {
                            values[i] = ValString.create(object.toString());
                        }
                    }
                }

                if (values != null) {
                    resultReceiver.receive(new Values(values));
                }
            } catch (final RuntimeException e) {
                error(e.getMessage(), e);
            }
        }

        @Override
        public void streamDocListInfo(final long numFound, final long start, final Float maxScore) {
            docListInfo = new DocListInfo(numFound, start, maxScore);
            LOGGER.debug(() -> "streamDocListInfo() - " + docListInfo);
        }

        private void error(final String message, final Throwable t) {
            if (errorReceiver == null) {
                LOGGER.error(() -> message, t);
            } else {
                errorReceiver.log(Severity.ERROR, null, null, message, t);
            }
        }

        DocListInfo getDocListInfo() {
            return docListInfo;
        }
    }

    private static class DocListInfo {
        private final long numFound;
        private final long start;
        private final Float maxScore;

        DocListInfo(final long numFound, final long start, final Float maxScore) {
            this.numFound = numFound;
            this.start = start;
            this.maxScore = maxScore;
        }

        long getNumFound() {
            return numFound;
        }

        long getStart() {
            return start;
        }

        Float getMaxScore() {
            return maxScore;
        }

        @Override
        public String toString() {
            return "DocListInfo{" +
                    "numFound=" + numFound +
                    ", start=" + start +
                    ", maxScore=" + maxScore +
                    '}';
        }
    }

    private static class Callback2 implements FastStreamingDocsCallback {
        private final AtomicLong count;
        private final String[] fieldNames;
        private final Map<String, SolrIndexField> fieldsMap;
        private final ResultReceiver resultReceiver;
        private final ErrorReceiver errorReceiver;

        Callback2(final AtomicLong count,
                  final String[] fieldNames,
                  final Map<String, SolrIndexField> fieldsMap,
                  final ResultReceiver resultReceiver,
                  final ErrorReceiver errorReceiver) {
            this.count = count;
            this.fieldNames = fieldNames;
            this.fieldsMap = fieldsMap;
            this.resultReceiver = resultReceiver;
            this.errorReceiver = errorReceiver;
        }

        final Map<String, Val> map = new HashMap<>();

        @Override
        public Object startDoc(final Object docListObj) {
            LOGGER.debug(() -> "startDoc() - docListObj=" + docListObj);
            return null;
        }

        @Override
        public void field(final DataEntry entry, final Object docObj) {
            if (entry.name() != null && entry.val() != null) {
                final Val val = convertFieldValue(entry);
                map.put(entry.name().toString(), val);
            }
        }

        @Override
        public void endDoc(final Object docObj) {
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted");
            }

            getStoredData(map);
            map.clear();

            count.incrementAndGet();
        }

        private Val convertFieldValue(final DataEntry entry) {
            switch (entry.type()) {
                case BOOL:
                    return ValBoolean.create(entry.boolVal());
                case BYTEARR:
                    return ValErr.create("Byte array not supported");
                case DATE:
                    return ValLong.create(entry.longVal());
                case DOUBLE:
                    return ValDouble.create(entry.doubleVal());
                case ENTRY_ITER:
                    final Object object = entry.val();
                    if (object instanceof Collection) {
                        final Collection collection = (Collection) object;
                        if (collection.size() > 1) {
                            return ValErr.create("Multi value");
                        } else if (collection.size() == 1) {
                            return ValString.create(collection.iterator().next().toString());
                        }
                    }
                    return ValString.create(entry.strValue());
//                return ValErr.create("Entry iterator not supported");
                case FLOAT:
                    return ValDouble.create(entry.floatVal());
                case INT:
                    return ValInteger.create(entry.intVal());
                case JAVA_OBJ:
                    return ValString.create(entry.strValue());
//                return ValErr.create("Java object not supported");
                case KEYVAL_ITER:
                    return ValString.create(entry.strValue());
//                return ValErr.create("Key value iterator not supported");
                case LONG:
                    return ValLong.create(entry.longVal());
                case NULL:
                    return ValNull.INSTANCE;
                case STR:
                    return ValString.create(entry.strValue());
            }
            return ValErr.create("Unknown type");
        }

        /**
         * This method takes a list of document id's and extracts the stored fields
         * that are required for data display. In some cases such as batch search we
         * only want to get stream and event ids, in these cases no values are
         * retrieved, only stream and event ids.
         */
        private void getStoredData(final Map<String, Val> valueMap) {
            try {
                Val[] values = null;

                for (int i = 0; i < fieldNames.length; i++) {
                    final String storedField = fieldNames[i];
                    final Val value = valueMap.get(storedField);
                    if (value != null) {
                        final SolrIndexField solrIndexField = fieldsMap.get(storedField);
                        // If the field is not in fact stored then it will be null here.
                        if (solrIndexField != null) {
                            if (values == null) {
                                values = new Val[fieldNames.length];
                            }
                            values[i] = value;
                        }
                    }
                }

                if (values != null) {
                    resultReceiver.receive(new Values(values));
                }
            } catch (final RuntimeException e) {
                error(e.getMessage(), e);
            }
        }

        private void error(final String message, final Throwable t) {
            if (errorReceiver == null) {
                LOGGER.error(() -> message, t);
            } else {
                errorReceiver.log(Severity.ERROR, null, null, message, t);
            }
        }
    }
}
