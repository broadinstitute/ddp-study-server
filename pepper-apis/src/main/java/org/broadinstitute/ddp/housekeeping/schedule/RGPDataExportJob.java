package org.broadinstitute.ddp.housekeeping.schedule;

import java.time.Instant;
import java.time.ZoneId;
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
public class RGPDataExportJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(RGPDataExportJob.class);

    private static DataExporter exporter;

    public static JobKey getKey() {
        return Keys.Export.RGPExportJob;
    }

    public static void register(Scheduler scheduler, Config cfg) throws SchedulerException {
        // Test that we can initialize the credentials.
        GoogleCredentialUtil.initCredentials(true);

        exporter = new DataExporter(cfg);
        JobDetail job = JobBuilder.newJob(RGPDataExportJob.class)
                .withIdentity(getKey())
                .requestRecovery(false)
                .storeDurably(true)
                .build();
        scheduler.addJob(job, true);
        LOG.info("Added job {} to scheduler", getKey());

        String schedule = ConfigUtil.getStrIfPresent(cfg, ConfigFile.RGP_EXPORT_SCHEDULE);
        if (schedule == null || schedule.equalsIgnoreCase("off")) {
            LOG.warn("Job {} is set to be turned off, no trigger added", getKey());
            return;
        }

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(Keys.Export.RGPExportTrigger)
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

        String gcpProjectId = cfg.getString(ConfigFile.GOOGLE_PROJECT_ID);
        GoogleCredentials credentials = GoogleCredentialUtil.initCredentials(true);
        if (credentials == null) {
            LOG.error("No Google credentials are provided, skipping job {}", getKey());
            return;
        }

        String bucketName = cfg.getString(ConfigFile.RGP_EXPORT_BUCKET);
        var bucketClient = new GoogleBucketClient(gcpProjectId, credentials);
        Bucket bucket = bucketClient.getRGPBucket(bucketName);
        if (bucket == null) {
            LOG.error("Could not find google bucket {}, skipping job {}", bucketName, getKey());
            return;
        }

        StudyDto rgpDto = TransactionWrapper.withTxn(TransactionWrapper.DB.APIS,
                handle -> handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid("RGP"));
        LOG.info("Found RGP study for data export");

        // Invalidate the caches for a fresh export
        ActivityDefStore.getInstance().clear();
        DataExporter.clearCachedAuth0Emails();

        var coordinator = new DataExportCoordinator(exporter)
                .includeCsv(bucket);

        try {
            boolean success = coordinator.exportRGP(rgpDto);
            //TODO: Make sure we only export surveys completed since last run
            //TODO: Make sure we only export relevant survey fields
            //TODO: Make sure we only export relevant participant fields
            //TODO: Make sure FirstFields are first
            //TODO: Send email
            if (success) {
                new StackdriverMetricsTracker(StackdriverCustomMetric.RGP_EXPORTS,
                        "RGP", PointsReducerFactory.buildSumReducer())
                        .addPoint(1, Instant.now().toEpochMilli());
            }
        } catch (Exception e) {
            LOG.error("Error while doing RGP export", e);
        }

    }
}
