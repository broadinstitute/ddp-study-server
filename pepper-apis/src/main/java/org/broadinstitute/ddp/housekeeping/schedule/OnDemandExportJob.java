package org.broadinstitute.ddp.housekeeping.schedule;

import java.time.Instant;
import java.util.List;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.elastic.ElasticSearchIndexType;
import org.broadinstitute.ddp.export.ActivityExtract;
import org.broadinstitute.ddp.export.DataExporter;
import org.broadinstitute.ddp.model.study.Participant;
import org.broadinstitute.ddp.util.ConfigManager;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OnDemandExportJob implements Job {

    public static final String DATA_INDEX = "index";
    public static final String DATA_STUDY = "study";
    public static final String INDEX_ALL = "all";

    private static final Logger LOG = LoggerFactory.getLogger(OnDemandExportJob.class);

    public static JobKey getKey() {
        return Keys.Export.OnDemandJob;
    }

    public static void register(Scheduler scheduler, Config cfg) throws SchedulerException {
        JobDetail job = JobBuilder.newJob(OnDemandExportJob.class)
                .withIdentity(getKey())
                .requestRecovery(false)
                .storeDurably(true)
                .build();

        // Add job without a trigger schedule since it's by on-demand.
        scheduler.addJob(job, true);
        LOG.info("Added job {} to scheduler", getKey());
    }

    @Override
    public void execute(JobExecutionContext ctx) throws JobExecutionException {
        try {
            JobDataMap data = ctx.getMergedJobDataMap();
            String index = data.getString(DATA_INDEX);
            String study = data.getString(DATA_STUDY);
            LOG.info("Triggered on-demand export job for index={} and study={}", index, study);

            boolean exportCurrentlyRunning = ctx.getScheduler()
                    .getCurrentlyExecutingJobs().stream()
                    .anyMatch(jctx -> {
                        JobKey key = jctx.getJobDetail().getKey();
                        return key.equals(StudyDataExportJob.getKey()) || key.equals(DataSyncJob.getKey());
                    });
            if (exportCurrentlyRunning) {
                LOG.warn("Regular data export or sync job currently running, skipping on-demand export job");
                return;
            }

            LOG.info("Running job {}", getKey());
            long start = Instant.now().toEpochMilli();
            run(index, study);
            long elapsed = Instant.now().toEpochMilli() - start;
            LOG.info("Finished job {}. Took {}s", getKey(), elapsed / 1000);
        } catch (Exception e) {
            LOG.error("Error while executing job {}", getKey(), e);
            throw new JobExecutionException(e, false);
        }
    }

    private void run(String index, String studyGuid) {
        Config cfg = ConfigManager.getInstance().getConfig();
        var exporter = new DataExporter(cfg);

        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
            if (!studyDto.isDataExportEnabled()) {
                LOG.warn("Study {} does not have data export enabled, skipping data export", studyGuid);
                return;
            }

            try {
                if (index.equals(INDEX_ALL)) {
                    LOG.info("Running {} elasticsearch export for study {}",
                            ElasticSearchIndexType.ACTIVITY_DEFINITION, studyGuid);
                    long start = Instant.now().toEpochMilli();
                    List<ActivityExtract> activities = exporter.exportActivityDefinitionsToElasticsearch(handle, studyDto, cfg);
                    long elapsed = Instant.now().toEpochMilli() - start;
                    LOG.info("Finished {} elasticsearch export for study {} in {}s",
                            ElasticSearchIndexType.ACTIVITY_DEFINITION, studyGuid, elapsed / 1000);

                    List<Participant> participants = exporter.extractParticipantDataSet(handle, studyDto);
                    runEsExport(studyGuid, ElasticSearchIndexType.PARTICIPANTS_STRUCTURED,
                            () -> exporter.exportToElasticsearch(handle, studyDto, activities, participants, true));
                    runEsExport(studyGuid, ElasticSearchIndexType.PARTICIPANTS,
                            () -> exporter.exportToElasticsearch(handle, studyDto, activities, participants, false));
                    runEsExport(studyGuid, ElasticSearchIndexType.USERS,
                            () -> exporter.exportUsersToElasticsearch(handle, studyDto, null));
                } else if (index.equals(ElasticSearchIndexType.ACTIVITY_DEFINITION.getElasticSearchCompatibleLabel())) {
                    runEsExport(studyGuid, ElasticSearchIndexType.ACTIVITY_DEFINITION,
                            () -> exporter.exportActivityDefinitionsToElasticsearch(handle, studyDto, cfg));
                } else if (index.equals(ElasticSearchIndexType.PARTICIPANTS_STRUCTURED.getElasticSearchCompatibleLabel())) {
                    runEsExport(studyGuid, ElasticSearchIndexType.PARTICIPANTS_STRUCTURED,
                            () -> exporter.exportParticipantsToElasticsearchByIds(handle, studyDto, null, true));
                } else if (index.equals(ElasticSearchIndexType.PARTICIPANTS.getElasticSearchCompatibleLabel())) {
                    runEsExport(studyGuid, ElasticSearchIndexType.PARTICIPANTS,
                            () -> exporter.exportParticipantsToElasticsearchByIds(handle, studyDto, null, false));
                } else if (index.equals(ElasticSearchIndexType.USERS.getElasticSearchCompatibleLabel())) {
                    runEsExport(studyGuid, ElasticSearchIndexType.USERS,
                            () -> exporter.exportUsersToElasticsearch(handle, studyDto, null));
                } else {
                    LOG.error("Unknown index type: {}", index);
                }
            } catch (Exception e) {
                LOG.error("Error while exporting data for study {}, continuing", studyGuid, e);
            }
        });
    }

    private void runEsExport(String studyGuid, ElasticSearchIndexType index, Runnable callback) {
        try {
            LOG.info("Running {} elasticsearch export for study {}", index, studyGuid);
            long start = Instant.now().toEpochMilli();
            callback.run();
            long elapsed = Instant.now().toEpochMilli() - start;
            LOG.info("Finished {} elasticsearch export for study {} in {}s", index, studyGuid, elapsed / 1000);
        } catch (Exception e) {
            LOG.error("Error while running {} elasticsearch export for study {}, continuing", index, studyGuid, e);
        }
    }
}
