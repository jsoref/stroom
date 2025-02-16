/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.search.impl;

import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.index.impl.IndexStore;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexFieldsMap;
import stroom.meta.shared.MetaService;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.errorhandler.MessageUtil;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Param;
import stroom.query.common.v2.Coprocessor;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.common.v2.Payload;
import stroom.search.extraction.ExtractionConfig;
import stroom.search.extraction.ExtractionTaskExecutor;
import stroom.search.extraction.ExtractionTaskHandler;
import stroom.search.extraction.ExtractionTaskProducer;
import stroom.search.extraction.StreamMapCreator;
import stroom.search.extraction.Values;
import stroom.search.impl.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.search.impl.shard.IndexShardSearchConfig;
import stroom.search.impl.shard.IndexShardSearchTask.IndexShardQueryFactory;
import stroom.search.impl.shard.IndexShardSearchTaskExecutor;
import stroom.search.impl.shard.IndexShardSearchTaskHandler;
import stroom.search.impl.shard.IndexShardSearchTaskProducer;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskCallback;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskHandler;
import stroom.task.api.TaskTerminatedException;
import stroom.task.shared.ThreadPool;
import stroom.task.shared.ThreadPoolImpl;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Location;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;


class ClusterSearchTaskHandler implements TaskHandler<ClusterSearchTask, NodeResult>, ErrorReceiver {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ClusterSearchTaskHandler.class);

    private final IndexStore indexStore;
    private final WordListProvider wordListProvider;
    private final TaskContext taskContext;
    private final CoprocessorFactory coprocessorFactory;
    private final IndexShardSearchTaskExecutor indexShardSearchTaskExecutor;
    private final IndexShardSearchConfig indexShardSearchConfig;
    private final ExtractionTaskExecutor extractionTaskExecutor;
    private final ExtractionConfig extractionConfig;
    private final MetaService metaService;
    private final SecurityContext securityContext;
    private final int maxBooleanClauseCount;
    private final int maxStoredDataQueueSize;
    private final LinkedBlockingQueue<String> errors = new LinkedBlockingQueue<>();
    private final CountDownLatch searchCompleteLatch = new CountDownLatch(1);
    private final Provider<IndexShardSearchTaskHandler> indexShardSearchTaskHandlerProvider;
    private final Provider<ExtractionTaskHandler> extractionTaskHandlerProvider;
    private final ExecutorProvider executorProvider;

    private ClusterSearchTask task;

    private LinkedBlockingQueue<Values> storedData;

    @Inject
    ClusterSearchTaskHandler(final IndexStore indexStore,
                             final WordListProvider wordListProvider,
                             final TaskContext taskContext,
                             final CoprocessorFactory coprocessorFactory,
                             final IndexShardSearchTaskExecutor indexShardSearchTaskExecutor,
                             final IndexShardSearchConfig indexShardSearchConfig,
                             final ExtractionTaskExecutor extractionTaskExecutor,
                             final ExtractionConfig extractionConfig,
                             final MetaService metaService,
                             final SecurityContext securityContext,
                             final SearchConfig searchConfig,
                             final Provider<IndexShardSearchTaskHandler> indexShardSearchTaskHandlerProvider,
                             final Provider<ExtractionTaskHandler> extractionTaskHandlerProvider,
                             final ExecutorProvider executorProvider) {
        this.indexStore = indexStore;
        this.wordListProvider = wordListProvider;
        this.taskContext = taskContext;
        this.coprocessorFactory = coprocessorFactory;
        this.indexShardSearchTaskExecutor = indexShardSearchTaskExecutor;
        this.indexShardSearchConfig = indexShardSearchConfig;
        this.extractionTaskExecutor = extractionTaskExecutor;
        this.extractionConfig = extractionConfig;
        this.metaService = metaService;
        this.securityContext = securityContext;
        this.maxBooleanClauseCount = searchConfig.getMaxBooleanClauseCount();
        this.maxStoredDataQueueSize = searchConfig.getMaxStoredDataQueueSize();
        this.indexShardSearchTaskHandlerProvider = indexShardSearchTaskHandlerProvider;
        this.extractionTaskHandlerProvider = extractionTaskHandlerProvider;
        this.executorProvider = executorProvider;
    }

    @Override
    public void exec(final ClusterSearchTask task, final TaskCallback<NodeResult> callback) {
        securityContext.useAsRead(() -> {
            if (!Thread.currentThread().isInterrupted()) {
                taskContext.info("Initialising...");

                this.task = task;
                final stroom.query.api.v2.Query query = task.getQuery();
                ResultSender resultSender = null;

                try {
                    final long frequency = task.getResultSendFrequency();

                    // Reload the index.
                    final IndexDoc index = indexStore.readDocument(query.getDataSource());

                    // Make sure we have a search index.
                    if (index == null) {
                        throw new SearchException("Search index has not been set");
                    }

                    // Get the stored fields that search is hoping to use.
                    final String[] storedFields = task.getStoredFields();
                    if (storedFields == null || storedFields.length == 0) {
                        throw new SearchException("No stored fields have been requested");
                    }

                    // Get an array of stored index fields that will be used for getting stored data.
                    final FieldIndexMap storedFieldIndexMap = new FieldIndexMap();
                    for (final String storedField : storedFields) {
                        storedFieldIndexMap.create(storedField, true);
                    }

                    // See if we need to filter steams and if any of the coprocessors need us to extract data.
                    boolean filterStreams;

                    Map<CoprocessorKey, Coprocessor> coprocessorMap = null;
                    Map<DocRef, Set<Coprocessor>> extractionCoprocessorsMap = null;

                    final FieldIndexMap extractionFieldIndexMap = new FieldIndexMap(true);

                    filterStreams = true;

                    // Create a map of index fields keyed by name.
                    final IndexFieldsMap indexFieldsMap = new IndexFieldsMap(index.getFields());

                    // Compile all of the result component options to optimise pattern matching etc.
                    if (task.getCoprocessorMap() != null) {
                        coprocessorMap = new HashMap<>();
                        extractionCoprocessorsMap = new HashMap<>();

                        for (final Entry<CoprocessorKey, CoprocessorSettings> entry : task.getCoprocessorMap().entrySet()) {
                            final CoprocessorKey coprocessorId = entry.getKey();
                            final CoprocessorSettings coprocessorSettings = entry.getValue();

                            // Figure out where the fields required by this coprocessor will be found.
                            FieldIndexMap fieldIndexMap = storedFieldIndexMap;
                            if (coprocessorSettings.extractValues() && coprocessorSettings.getExtractionPipeline() != null
                                    && coprocessorSettings.getExtractionPipeline().getUuid() != null) {
                                fieldIndexMap = extractionFieldIndexMap;
                            }

                            // Create a parameter map.
                            final Map<String, String> paramMap;
                            if (query.getParams() != null) {
                                paramMap = query.getParams().stream()
                                        .collect(Collectors.toMap(Param::getKey, Param::getValue));
                            } else {
                                paramMap = Collections.emptyMap();
                            }
                            final Coprocessor coprocessor = coprocessorFactory.create(coprocessorSettings, fieldIndexMap, paramMap, taskContext);

                            if (coprocessor != null) {
                                coprocessorMap.put(coprocessorId, coprocessor);

                                // Find out what data extraction might be needed for the coprocessors.
                                DocRef pipelineRef = null;
                                if (coprocessorSettings.extractValues()
                                        && coprocessorSettings.getExtractionPipeline() != null) {
                                    pipelineRef = coprocessorSettings.getExtractionPipeline();
                                    filterStreams = true;
                                }

                                extractionCoprocessorsMap.computeIfAbsent(pipelineRef, k -> new HashSet<>()).add(coprocessor);
                            }
                        }
                    }

                    // Start forwarding data to target node.
                    final Executor executor = executorProvider.getExecutor(ResultSender.THREAD_POOL);
                    resultSender = new ResultSender(searchCompleteLatch, coprocessorMap, callback, frequency, executor, taskContext, errors);
                    resultSender.sendData();

                    // Start searching.
                    search(task, query, storedFields, filterStreams, indexFieldsMap, extractionFieldIndexMap, extractionCoprocessorsMap);

                } catch (final RuntimeException e) {
                    try {
                        callback.onFailure(e);
                    } catch (final RuntimeException e2) {
                        // If we failed to send the result or the source node rejected the result because the source task has been terminated then terminate the task.
                        LOGGER.info(() -> "Terminating search because we were unable to send result");
                        taskContext.terminate();
                    }
                } finally {
                    LOGGER.trace(() -> "Search is complete, setting searchComplete to true and " +
                            "counting down searchCompleteLatch");

                    // countDown the latch so sendData knows we are complete
                    searchCompleteLatch.countDown();

                    try {
                        if (resultSender != null) {
                            // Now we must wait for results to be sent to the requesting node.
                            taskContext.info("Sending final results");
                            resultSender.awaitCompletion();
                        }
                    } catch (final InterruptedException e) {
                        try {
                            callback.onFailure(e);
                        } catch (final RuntimeException e2) {
                            // If we failed to send the result or the source node rejected the result because the source task has been terminated then terminate the task.
                            LOGGER.info(() -> "Terminating search because we have been interrupted");
                            taskContext.terminate();
                        }

                        // Continue to interrupt this thread.
                        Thread.currentThread().interrupt();

                    } catch (final RuntimeException e) {
                        try {
                            callback.onFailure(e);
                        } catch (final RuntimeException e2) {
                            // If we failed to send the result or the source node rejected the result because the source task has been terminated then terminate the task.
                            LOGGER.info(() -> "Terminating search because we were unable to send result");
                            taskContext.terminate();
                        }
                    }
                }
            }
        });
    }

    private void search(final ClusterSearchTask task,
                        final stroom.query.api.v2.Query query,
                        final String[] storedFieldNames,
                        final boolean filterStreams,
                        final IndexFieldsMap indexFieldsMap,
                        final FieldIndexMap extractionFieldIndexMap,
                        final Map<DocRef, Set<Coprocessor>> extractionCoprocessorsMap) {
        taskContext.info("Searching...");
        LOGGER.debug(() -> "Incoming search request:\n" + query.getExpression().toString());

        try {
            if (extractionCoprocessorsMap != null && extractionCoprocessorsMap.size() > 0
                    && task.getShards().size() > 0) {
                // Make sure we are searching a specific index.
                if (query.getExpression() == null) {
                    throw new SearchException("Search expression has not been set");
                }

                // Search all index shards.
                final Map<Version, SearchExpressionQuery> queryMap = new HashMap<>();

                final IndexShardQueryFactory queryFactory = createIndexShardQueryFactory(
                        task, query, indexFieldsMap, queryMap);

                // Create a transfer list to capture stored data from the index that can be used by coprocessors.
                storedData = new LinkedBlockingQueue<>(maxStoredDataQueueSize);
                final AtomicLong hitCount = new AtomicLong();

                // Update config for the index shard search task executor.
                indexShardSearchTaskExecutor.setMaxThreads(indexShardSearchConfig.getMaxThreads());

                // Make a task producer that will create event data extraction tasks when requested by the executor.
                final Executor searchExecutor = executorProvider.getExecutor(IndexShardSearchTaskProducer.THREAD_POOL);
                final IndexShardSearchTaskProducer indexShardSearchTaskProducer = new IndexShardSearchTaskProducer(
                        indexShardSearchTaskExecutor,
                        storedData,
                        task.getShards(),
                        queryFactory,
                        storedFieldNames,
                        this,
                        hitCount,
                        indexShardSearchConfig.getMaxThreadsPerTask(),
                        searchExecutor,
                        indexShardSearchTaskHandlerProvider);

                if (!filterStreams) {
                    // If we aren't required to filter streams and aren't using pipelines to feed data to coprocessors then just do a simple data transfer to the coprocessors.
                    transfer(extractionCoprocessorsMap);

                } else {
                    // Update config for extraction task executor.
                    extractionTaskExecutor.setMaxThreads(extractionConfig.getMaxThreads());

                    // Create an object to make event lists from raw index data.
                    final StreamMapCreator streamMapCreator = new StreamMapCreator(task.getStoredFields(), this,
                            metaService, securityContext);

                    // Make a task producer that will create event data extraction tasks when requested by the executor.
                    final Executor extractionExecutor = executorProvider.getExecutor(ExtractionTaskProducer.THREAD_POOL);
                    final ExtractionTaskProducer extractionTaskProducer = new ExtractionTaskProducer(
                            extractionTaskExecutor,
                            streamMapCreator,
                            storedData,
                            extractionFieldIndexMap,
                            extractionCoprocessorsMap,
                            this,
                            extractionConfig.getMaxThreadsPerTask(),
                            extractionExecutor,
                            extractionTaskHandlerProvider);

                    // Await completion.
                    indexShardSearchTaskProducer.awaitCompletion();
                    extractionTaskProducer.awaitCompletion();
                }
            }
        } catch (final InterruptedException e) {
            // Continue to interrupt this thread.
            Thread.currentThread().interrupt();

            throw SearchException.wrap(e);
        } catch (final RuntimeException e) {
            throw SearchException.wrap(e);
        }
    }

    private IndexShardQueryFactory createIndexShardQueryFactory(final ClusterSearchTask task, final stroom.query.api.v2.Query query, final IndexFieldsMap indexFieldsMap, final Map<Version, SearchExpressionQuery> queryMap) {
        return new IndexShardQueryFactory() {

            @Override
            public Query getQuery(final Version luceneVersion) {
                SearchExpressionQuery searchExpressionQuery = queryMap.get(luceneVersion);
                if (searchExpressionQuery == null) {
                    // Get a query for the required lucene version.
                    searchExpressionQuery = getQuery(luceneVersion, query.getExpression(), indexFieldsMap);
                    queryMap.put(luceneVersion, searchExpressionQuery);
                }

                return searchExpressionQuery.getQuery();
            }

            private SearchExpressionQuery getQuery(final Version version, final ExpressionOperator expression,
                                                   final IndexFieldsMap indexFieldsMap1) {
                SearchExpressionQuery query = null;
                try {
                    final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
                            wordListProvider,
                            indexFieldsMap1,
                            maxBooleanClauseCount,
                            task.getDateTimeLocale(),
                            task.getNow());
                    final SearchExpressionQuery searchExpressionQuery = searchExpressionQueryBuilder.buildQuery(version, expression);

                    // Make sure the query was created successfully.
                    if (searchExpressionQuery.getQuery() == null) {
                        throw new SearchException("Failed to build Lucene query given expression");
                    } else {
                        LOGGER.debug(() -> "Lucene Query is " + searchExpressionQuery.toString());
                    }
                    query = searchExpressionQuery;

                } catch (final RuntimeException e) {
                    error(e.getMessage(), e);
                }

                if (query == null) {
                    query = new SearchExpressionQuery(null, null);
                }

                return query;
            }
        };
    }

    private void transfer(final Map<DocRef, Set<Coprocessor>> extractionCoprocessorsMap) throws InterruptedException {
        try {
            // If we aren't required to filter streams and aren't using pipelines to feed data to coprocessors then just do a simple data transfer to the coprocessors.
            final Set<Coprocessor> coprocessors = extractionCoprocessorsMap.get(null);
            boolean complete = false;
            while (!complete) {
                // Take the next stored data result.
                final Values values = storedData.take();
                if (values.complete()) {
                    complete = true;
                } else {
                    // Send the data to all coprocessors.
                    for (final Coprocessor coprocessor : coprocessors) {
                        coprocessor.receive(values.getValues());
                    }
                }
            }
        } catch (final RuntimeException e) {
            error(e.getMessage(), e);
        }
    }

    private void error(final String message, final Throwable t) {
        log(Severity.ERROR, null, null, message, t);
    }

    @Override
    public void log(final Severity severity, final Location location, final String elementId, final String message,
                    final Throwable e) {
        if (e != null) {
            LOGGER.debug(e::getMessage, e);
        }

        if (!(e instanceof TaskTerminatedException)) {
            final String msg = MessageUtil.getMessage(message, e);
            try {
                errors.put(msg);
            } catch (final InterruptedException ie) {
                // Continue to interrupt this thread.
                Thread.currentThread().interrupt();
            }
        }
    }

    public ClusterSearchTask getTask() {
        return task;
    }

    private static class ResultSender {
        private static final ThreadPool THREAD_POOL = new ThreadPoolImpl(
                "Search Result Sender",
                5,
                0,
                Integer.MAX_VALUE);

        private final CountDownLatch searchCompleteLatch;
        private final Map<CoprocessorKey, Coprocessor> coprocessorMap;
        private final TaskCallback<NodeResult> callback;
        private final long frequency;
        private final Executor executor;
        private final TaskContext taskContext;
        private final LinkedBlockingQueue<String> errors;

        private final CountDownLatch sendingDataComplete = new CountDownLatch(1);

        ResultSender(final CountDownLatch searchCompleteLatch,
                     final Map<CoprocessorKey, Coprocessor> coprocessorMap,
                     final TaskCallback<NodeResult> callback,
                     final long frequency,
                     final Executor executor,
                     final TaskContext taskContext,
                     final LinkedBlockingQueue<String> errors) {
            this.searchCompleteLatch = searchCompleteLatch;
            this.coprocessorMap = coprocessorMap;
            this.callback = callback;
            this.frequency = frequency;
            this.executor = executor;
            this.taskContext = taskContext;
            this.errors = errors;
        }

        void sendData() {
            final long now = System.currentTimeMillis();

            LOGGER.trace(() -> "sendData() called");

            final Runnable runnable = () -> {
                try {
                    // Wait until the next send frequency time or drop out as soon as the search completes and the latch is counted down.
                    // Compute the wait time as we may have used up some of the frequency getting to here
                    final long waitTime = System.currentTimeMillis() - now + frequency;
                    LOGGER.trace(() -> "frequency [" + frequency + "], waitTime [" + waitTime + "]");
                    final boolean searchComplete = searchCompleteLatch.await(waitTime, TimeUnit.MILLISECONDS);

                    try {
                        if (!Thread.currentThread().isInterrupted()) {
                            taskContext.setName("Search Result Sender");
                            taskContext.info("Creating search result");

                            // Produce payloads for each coprocessor.
                            Map<CoprocessorKey, Payload> payloadMap = null;
                            if (coprocessorMap != null && coprocessorMap.size() > 0) {
                                for (final Entry<CoprocessorKey, Coprocessor> entry : coprocessorMap.entrySet()) {
                                    final Payload payload = entry.getValue().createPayload();
                                    if (payload != null) {
                                        if (payloadMap == null) {
                                            payloadMap = new HashMap<>();
                                        }

                                        payloadMap.put(entry.getKey(), payload);
                                    }
                                }
                            }

                            // Drain all current errors to a list.
                            List<String> errorsSnapshot = new ArrayList<>();
                            errors.drainTo(errorsSnapshot);
                            if (errorsSnapshot.size() == 0) {
                                errorsSnapshot = null;
                            }

                            // Only send a result if we have something new to send.
                            if (payloadMap != null || errorsSnapshot != null || searchComplete) {
                                // Form a result to send back to the requesting node.
                                final NodeResult result = new NodeResult(payloadMap, errorsSnapshot, searchComplete);

                                // Give the result to the callback.
                                taskContext.info("Sending search result");
                                callback.onSuccess(result);
                            }
                        }

                        // Make sure we don't continue to execute this task if it should have terminated.
                        if (Thread.currentThread().isInterrupted() || searchComplete) {
                            complete();
                        } else {
                            // Try to send more data.
                            sendData();
                        }

                    } catch (final RuntimeException e) {
                        complete();

                        // If we failed to send the result or the source node rejected the result because the source
                        // task has been terminated then terminate the task.
                        LOGGER.info(() -> "Terminating search because we were unable to send result");
                        taskContext.terminate();
                    }

                } catch (final InterruptedException e) {
                    complete();

                    // Continue to interrupt this thread.
                    Thread.currentThread().interrupt();
                }
            };

            // Run the sending code asynchronously.
            CompletableFuture.runAsync(runnable, executor);
        }

        private void complete() {
            // We have sent the last data we were expected to so tell the parent cluster search that we have finished sending data.
            sendingDataComplete.countDown();
            LOGGER.debug(() -> "sendingData is false");
        }

        void awaitCompletion() throws InterruptedException {
            sendingDataComplete.await();
        }
    }
}
