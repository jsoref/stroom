package stroom.data.store;

import stroom.task.api.job.ScheduledJob;
import stroom.task.api.job.ScheduledJobs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static stroom.task.api.job.Schedule.ScheduleType.CRON;
import static stroom.task.api.job.ScheduledJob.ScheduledJobBuilder.jobBuilder;

@Singleton
public class DataStoreJobs implements ScheduledJobs {

    private StreamRetentionExecutor streamRetentionExecutor;

    @Inject
    public DataStoreJobs(StreamRetentionExecutor streamRetentionExecutor) {
        this.streamRetentionExecutor = streamRetentionExecutor;
    }

    @Override
    public List<ScheduledJob> getJobs() {
        return List.of(
                jobBuilder()
                        .name("Stream Retention")
                        .description("Delete data that exceeds the retention period specified by feed")
                        .schedule(CRON, "0 0 *")
                        .method((task) -> this.streamRetentionExecutor.exec())
                        .build()
        );
    }
}
