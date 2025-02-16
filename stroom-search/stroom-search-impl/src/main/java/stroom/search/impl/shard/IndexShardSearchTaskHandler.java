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

package stroom.search.impl.shard;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.util.Version;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValString;
import stroom.index.impl.IndexShardService;
import stroom.index.impl.IndexShardWriter;
import stroom.index.impl.IndexShardWriterCache;
import stroom.index.impl.LuceneVersionUtil;
import stroom.index.shared.IndexShard;
import stroom.search.extraction.Values;
import stroom.search.impl.SearchException;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.util.logging.LambdaLogUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;
import java.io.IOException;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;

public class IndexShardSearchTaskHandler {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardSearchTaskHandler.class);

    private final IndexShardWriterCache indexShardWriterCache;
    private final IndexShardService indexShardService;
    private final IndexShardSearchConfig shardConfig;
    private final ExecutorProvider executorProvider;
    private final TaskContext taskContext;

    @Inject
    IndexShardSearchTaskHandler(final IndexShardWriterCache indexShardWriterCache,
                                final IndexShardService indexShardService,
                                final IndexShardSearchConfig shardConfig,
                                final ExecutorProvider executorProvider,
                                final TaskContext taskContext) {
        this.indexShardWriterCache = indexShardWriterCache;
        this.indexShardService = indexShardService;
        this.shardConfig = shardConfig;
        this.executorProvider = executorProvider;
        this.taskContext = taskContext;
    }

    public VoidResult exec(final IndexShardSearchTask task) {
        LOGGER.logDurationIfDebugEnabled(
                () -> {
                    final Long indexShardId = task.getIndexShardId();
                    IndexShardSearcher indexShardSearcher = null;

                    try {
                        taskContext.setName("Search Index Shard");
                        if (!Thread.currentThread().isInterrupted()) {
                            taskContext.info("Searching shard " + task.getShardNumber() + " of " + task.getShardTotal() + " (id="
                                    + task.getIndexShardId() + ")");


                            final IndexWriter indexWriter = getWriter(indexShardId);

                            final IndexShard indexShard = indexShardService.loadById(indexShardId);
                            if (indexShard == null) {
                                throw new SearchException("Unable to find index shard with id = " + indexShardId);
                            }

                            indexShardSearcher = new IndexShardSearcherImpl(indexShard, indexWriter);

                            // Start searching.
                            searchShard(task, indexShardSearcher);
                        }
                    } catch (final RuntimeException e) {
                        LOGGER.debug(e::getMessage, e);
                        error(task, e.getMessage(), e);

                    } finally {
                        taskContext.setName("Closing searcher");
                        taskContext.info("Closing searcher for index shard " + indexShardId);
                        if (indexShardSearcher != null) {
                            indexShardSearcher.destroy();
                        }
                    }
                },
                LambdaLogUtil.message("exec() for shard {}", task.getShardNumber()));

        return VoidResult.INSTANCE;
    }

    private IndexWriter getWriter(final Long indexShardId) {
        IndexWriter indexWriter = null;

        // Load the current index shard.
        final IndexShardWriter indexShardWriter = indexShardWriterCache.getWriterByShardId(indexShardId);
        if (indexShardWriter != null) {
            indexWriter = indexShardWriter.getWriter();
        }

        return indexWriter;
    }

    private void searchShard(final IndexShardSearchTask task, final IndexShardSearcher indexShardSearcher) {
        // Get the index shard that this searcher uses.
        final IndexShard indexShard = indexShardSearcher.getIndexShard();
        // Get the Lucene version being used.
        final Version luceneVersion = LuceneVersionUtil.getLuceneVersion(indexShard.getIndexVersion());
        // Get a query for this lucene version.
        final Query query = task.getQueryFactory().getQuery(luceneVersion);

        // If there is an error building the query then it will be null here.
        if (query != null) {
            final int maxDocIdQueueSize = shardConfig.getMaxDocIdQueueSize();
            LOGGER.debug(() -> "Creating docIdStore with size " + maxDocIdQueueSize);
            final LinkedBlockingQueue<OptionalInt> docIdStore = new LinkedBlockingQueue<>(maxDocIdQueueSize);

            // Create a collector.
            final IndexShardHitCollector collector = new IndexShardHitCollector(docIdStore, task.getHitCount());

            try {
                final SearcherManager searcherManager = indexShardSearcher.getSearcherManager();
                final IndexSearcher searcher = searcherManager.acquire();
                try {
                    final Executor executor = executorProvider.getExecutor(IndexShardSearchTaskProducer.THREAD_POOL);
                    CompletableFuture.runAsync(() -> {
                        taskContext.setName("Index Searcher");
                        LOGGER.logDurationIfDebugEnabled(
                                () -> {
                                    try {
                                        searcher.search(query, collector);
                                    } catch (final IOException e) {
                                        error(task, e.getMessage(), e);
                                    }

                                    try {
                                        docIdStore.put(OptionalInt.empty());
                                    } catch (final InterruptedException e) {
                                        error(task, e.getMessage(), e);

                                        // Continue to interrupt this thread.
                                        Thread.currentThread().interrupt();
                                    }
                                },
                                () -> "searcher.search()");
                    }, executor);

                    // Start converting found docIds into stored data values
                    boolean complete = false;
                    while (!complete) {
                        // Take the next item
                        final OptionalInt optDocId = docIdStore.take();
                        if (optDocId.isPresent()) {
                            // If we have a doc id then retrieve the stored data for it.
                            getStoredData(task, searcher, optDocId.getAsInt());
                        } else {
                            complete = true;
                        }
                    }
                } catch (final RuntimeException e) {
                    error(task, e.getMessage(), e);
                } finally {
                    searcherManager.release(searcher);
                }
            } catch (final InterruptedException e) {
                error(task, e.getMessage(), e);

                // Continue to interrupt.
                Thread.currentThread().interrupt();
            } catch (final RuntimeException | IOException e) {
                error(task, e.getMessage(), e);
            }
        }
    }

    /**
     * This method takes a list of document id's and extracts the stored fields
     * that are required for data display. In some cases such as batch search we
     * only want to get stream and event ids, in these cases no values are
     * retrieved, only stream and event ids.
     */
    private void getStoredData(final IndexShardSearchTask task, final IndexSearcher searcher, final int docId) {
        final String[] fieldNames = task.getFieldNames();
        try {
            final Document document = searcher.doc(docId);
            Val[] values = null;

            for (int i = 0; i < fieldNames.length; i++) {
                final String storedField = fieldNames[i];
                final IndexableField indexableField = document.getField(storedField);

                // If the field is not in fact stored then it will be null here.
                if (indexableField != null) {
                    final String value = indexableField.stringValue();
                    if (value != null) {
                        final String trimmed = value.trim();
                        if (trimmed.length() > 0) {
                            if (values == null) {
                                values = new Val[fieldNames.length];
                            }
                            values[i] = ValString.create(trimmed);
                        }
                    }
                }
            }

            if (values != null) {
                task.getResultReceiver().receive(task.getIndexShardId(), new Values(values));
            }
        } catch (final IOException | RuntimeException e) {
            error(task, e.getMessage(), e);
        }
    }

    private void error(final IndexShardSearchTask task, final String message, final Throwable t) {
        if (task == null) {
            LOGGER.error(() -> message, t);
        } else {
            task.getErrorReceiver().log(Severity.ERROR, null, null, message, t);
        }
    }

//    private int getIntProperty(final String propertyName, final int defaultValue) {
//        int value = defaultValue;
//
//        final String string = propertyService.getProperty(propertyName);
//        if (string != null && string.length() > 0) {
//            try {
//                value = Integer.parseInt(string);
//            } catch (final NumberFormatException e) {
//                LOGGER.error(() -> "Unable to parse property '" + propertyName + "' value '" + string
//                        + "', using default of '" + defaultValue + "' instead", e);
//            }
//        }
//
//        return value;
//    }
}
