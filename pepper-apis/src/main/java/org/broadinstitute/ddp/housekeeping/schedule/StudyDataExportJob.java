package org.broadinstitute.ddp.housekeeping.schedule;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Bucket;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.client.GoogleBucketClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.elastic.ElasticSearchIndexType;
import org.broadinstitute.ddp.export.DataExportCoordinator;
import org.broadinstitute.ddp.export.DataExporter;
import org.broadinstitute.ddp.monitoring.PointsReducerFactory;
import org.broadinstitute.ddp.monitoring.StackdriverCustomMetric;
import org.broadinstitute.ddp.monitoring.StackdriverMetricsTracker;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GoogleCredentialUtil;
import org.quartz.CronScheduleBuilder;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisallowConcurrentExecution
public class StudyDataExportJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(StudyDataExportJob.class);

    public static JobKey getKey() {
        return Keys.Export.DataExportJob;
    }

    public static void register(Scheduler scheduler, Config cfg) throws SchedulerException {
        // Test that we can initialize the credentials.
        GoogleCredentialUtil.initCredentials(cfg.getBoolean(ConfigFile.REQUIRE_DEFAULT_GCP_CREDENTIALS));

        JobDetail job = JobBuilder.newJob(StudyDataExportJob.class)
                .withIdentity(getKey())
                .requestRecovery(false)
                .storeDurably(true)
                .build();
        scheduler.addJob(job, true);
        LOG.info("Added job {} to scheduler", getKey());

        String schedule = ConfigUtil.getStrIfPresent(cfg, ConfigFile.STUDY_EXPORT_SCHEDULE);
        if (schedule == null || schedule.equalsIgnoreCase("off")) {
            LOG.warn("Job {} is set to be turned off, no trigger added", getKey());
            return;
        }

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(Keys.Export.DataExportTrigger)
                .forJob(getKey())
                .withSchedule(CronScheduleBuilder
                        .cronSchedule(schedule)
                        .inTimeZone(TimeZone.getTimeZone(ZoneId.of("UTC")))
                        .withMisfireHandlingInstructionFireAndProceed())    // If missed or late, just fire it.
                .startNow()
                .build();
        scheduler.scheduleJob(trigger);
        LOG.info("Added trigger {} for job {} with schedule '{}'", trigger.getKey(), getKey(), schedule);
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            LOG.info("Running job {}", getKey());
            long start = Instant.now().toEpochMilli();
            run();
            long elapsed = Instant.now().toEpochMilli() - start;
            LOG.info("Finished job {}. Took {}s", getKey(), elapsed / 1000);
        } catch (Exception e) {
            LOG.error("Error while executing job {}", getKey(), e);
            throw new JobExecutionException(e, false);
        }
    }

    private void run() {
        Config cfg = ConfigManager.getInstance().getConfig();
        var exporter = new DataExporter(cfg);

        String gcpProjectId = cfg.getString(ConfigFile.GOOGLE_PROJECT_ID);
        String bucketName = cfg.getString(ConfigFile.STUDY_EXPORT_BUCKET);
        GoogleCredentials credentials = GoogleCredentialUtil
                .initCredentials(cfg.getBoolean(ConfigFile.REQUIRE_DEFAULT_GCP_CREDENTIALS));
        if (credentials == null) {
            LOG.error("No Google credentials are provided, skipping job {}", getKey());
            return;
        }

        var bucketClient = new GoogleBucketClient(gcpProjectId, credentials);
        Bucket bucket = bucketClient.getBucket(bucketName);
        if (bucket == null) {
            LOG.error("Could not find google bucket {}, skipping job {}", bucketName, getKey());
            return;
        }

        List<StudyDto> studyDtos = TransactionWrapper.withTxn(TransactionWrapper.DB.APIS,
                handle -> handle.attach(JdbiUmbrellaStudy.class).findAll());
        Collections.shuffle(studyDtos);
        LOG.info("Found {} studies for data export", studyDtos.size());

        // Invalidate the caches for a fresh export
        ActivityDefStore.getInstance().clear();
        DataExporter.clearCachedAuth0Emails();

        var coordinator = new DataExportCoordinator(exporter)
                .withBatchSize(cfg.getInt(ConfigFile.ELASTICSEARCH_EXPORT_BATCH_SIZE))
                .includeIndex(ElasticSearchIndexType.PARTICIPANTS_STRUCTURED)
                .includeIndex(ElasticSearchIndexType.PARTICIPANTS)
                .includeIndex(ElasticSearchIndexType.USERS)
                .includeCsv(bucket);

        for (var studyDto : studyDtos) {
            String studyGuid = studyDto.getGuid();
            if (!studyDto.isDataExportEnabled()) {
                LOG.warn("Study {} does not have data export enabled, skipping data export", studyGuid);
                continue;
            }

            try {
                boolean success = coordinator.export(studyDto);
                if (success) {
                    new StackdriverMetricsTracker(StackdriverCustomMetric.DATA_EXPORTS,
                            studyGuid, PointsReducerFactory.buildSumReducer())
                            .addPoint(1, Instant.now().toEpochMilli());
                }
            } catch (Exception e) {
                LOG.error("Error while exporting data for study {}, continuing", studyGuid, e);
            }
        }
    }
}
