package org.broadinstitute.ddp.customexport.housekeeping.schedule;

import java.time.Instant;
import java.time.ZoneId;
import java.util.TimeZone;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Bucket;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.client.GoogleBucketClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.customexport.constants.CustomExportConfigFile;
import org.broadinstitute.ddp.customexport.export.CustomExportCoordinator;
import org.broadinstitute.ddp.customexport.export.CustomExporter;
import org.broadinstitute.ddp.util.SecretUtil;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.housekeeping.schedule.Keys;
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
public class CustomExportJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(CustomExportJob.class);

    private static JobKey getKey() {
        return Keys.Export.CustomExportJob;
    }

    public static void register(Scheduler scheduler, Config cfg) throws SchedulerException {
        // Test that we can initialize the credentials.
        GoogleCredentialUtil.initCredentials(true);

        JobDetail job = JobBuilder.newJob(CustomExportJob.class)
                .withIdentity(getKey())
                .requestRecovery(false)
                .storeDurably(true)
                .build();
        scheduler.addJob(job, true);
        LOG.info("Added job {} to scheduler", getKey());

        String schedule = ConfigUtil.getStrIfPresent(cfg, ConfigFile.CUSTOM_EXPORT_SCHEDULE);
        if (schedule == null || schedule.equalsIgnoreCase("off")) {
            LOG.warn("Job {} is set to be turned off, no trigger added", getKey());
            return;
        }

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(Keys.Export.CustomExportTrigger)
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
            runExport();
            long elapsed = Instant.now().toEpochMilli() - start;
            LOG.info("Finished job {}. Took {} ms", getKey(), elapsed);
        } catch (Exception e) {
            LOG.error("Error while executing job {}", getKey(), e);
            throw new JobExecutionException(e, false);
        }
    }

    private void runExport() {
        Config cfg = ConfigManager.getInstance().getConfig();
        Config exportCfg = SecretUtil.getConfigFromSecret(cfg, ConfigFile.CUSTOM_EXPORT_CONFIG_SECRET);
        String gcpProjectId = cfg.getString(ConfigFile.GOOGLE_PROJECT_ID);
        GoogleCredentials credentials = GoogleCredentialUtil.initCredentials(true);
        if (credentials == null) {
            LOG.error("No Google credentials are provided, skipping job {}", getKey());
            return;
        }

        var bucketClient = new GoogleBucketClient(gcpProjectId, credentials);
        String bucketName = exportCfg.getString(CustomExportConfigFile.BUCKET_NAME);
        Bucket bucket = bucketClient.getBucketFromBlob(bucketName);
        if (bucket == null) {
            LOG.error("Could not find google bucket {}, skipping job {}", bucketName, getKey());
            return;
        }

        String studyGuid = exportCfg.getString(CustomExportConfigFile.GUID);

        StudyDto customDto = TransactionWrapper.withTxn(TransactionWrapper.DB.APIS,
                handle -> handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid));
        LOG.info("Found custom study for data export");

        // Invalidate the caches for a fresh export
        ActivityDefStore.getInstance().clear();
        CustomExporter.clearCachedAuth0Emails();

        var coordinator = new CustomExportCoordinator(exportCfg)
                .includeCsv(bucket);

        try {
            boolean success = coordinator.exportCustom(customDto);
            if (success) {
                new StackdriverMetricsTracker(StackdriverCustomMetric.CUSTOM_EXPORTS,
                        studyGuid, PointsReducerFactory.buildSumReducer())
                        .addPoint(1, Instant.now().toEpochMilli());
            }
        } catch (Exception e) {
            LOG.error("Error while doing custom export", e);
        }
    }
}
