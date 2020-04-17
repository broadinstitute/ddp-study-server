package org.broadinstitute.ddp.housekeeping.schedule;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.client.GoogleBucketClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.elastic.ElasticSearchIndexType;
import org.broadinstitute.ddp.export.ActivityExtract;
import org.broadinstitute.ddp.export.DataExporter;
import org.broadinstitute.ddp.model.study.Participant;
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
    private static final int READER_BUFFER_SIZE_IN_BYTES = 10 * 1024;

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
            long elapsed = estimateExecutionTime(this::run);
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

        for (var studyDto : studyDtos) {
            String studyGuid = studyDto.getGuid();
            if (!studyDto.isDataExportEnabled()) {
                LOG.warn("Study {} does not have data export enabled, skipping data export", studyGuid);
                continue;
            }

            try {
                boolean success = TransactionWrapper.withTxn(TransactionWrapper.DB.APIS, handle -> {
                    List<ActivityExtract> activities = exporter.extractActivities(handle, studyDto);
                    List<Participant> participants = exporter.extractParticipantDataSet(handle, studyDto);
                    boolean allSuccess;
                    boolean runSuccess;

                    runSuccess = runEsExport(studyGuid, ElasticSearchIndexType.PARTICIPANTS_STRUCTURED, () ->
                            exporter.exportToElasticsearch(handle, studyDto, activities, participants, true));
                    allSuccess = runSuccess;

                    runSuccess = runEsExport(studyGuid, ElasticSearchIndexType.PARTICIPANTS, () ->
                            exporter.exportToElasticsearch(handle, studyDto, activities, participants, false));
                    allSuccess = allSuccess && runSuccess;

                    runSuccess = runEsExport(studyGuid, ElasticSearchIndexType.USERS, () ->
                            exporter.exportUsersToElasticsearch(handle, studyDto, null));
                    allSuccess = allSuccess && runSuccess;

                    runSuccess = runCsvExport(studyGuid, () ->
                            exportStudyToGoogleBucket(studyDto, exporter, bucket, activities, participants));

                    return allSuccess && runSuccess;
                });
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

    private boolean runEsExport(String studyGuid, ElasticSearchIndexType index, Runnable callback) {
        try {
            LOG.info("Running {} elasticsearch export for study {}", index, studyGuid);
            long elapsed = estimateExecutionTime(callback);
            LOG.info("Finished {} elasticsearch export for study {} in {}s", index, studyGuid, elapsed / 1000);
            return true;
        } catch (Exception e) {
            LOG.error("Error while running {} elasticsearch export for study {}, continuing", index, studyGuid, e);
            return false;
        }
    }

    private boolean runCsvExport(String studyGuid, Runnable callback) {
        try {
            LOG.info("Running csv export for study {}", studyGuid);
            long elapsed = estimateExecutionTime(callback);
            LOG.info("Finished csv export for study {} in {}s", studyGuid, elapsed / 1000);
            return true;
        } catch (Exception e) {
            LOG.error("Error while running csv export for study {}, continuing", studyGuid, e);
            return false;
        }
    }

    boolean exportStudyToGoogleBucket(StudyDto studyDto, DataExporter exporter, Bucket bucket,
                                      List<ActivityExtract> activities,
                                      List<Participant> participants) {
        try (
                PipedOutputStream outputStream = new PipedOutputStream();
                Writer csvWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
                PipedInputStream csvInputStream = new PipedInputStream(outputStream, READER_BUFFER_SIZE_IN_BYTES);
        ) {
            // Running the DataExporter in separate thread
            Runnable csvExportRunnable = buildExportToCsvRunnable(studyDto, exporter, csvWriter, activities, participants);
            Thread csvExportThread = new Thread(csvExportRunnable);
            csvExportThread.start();

            String fileName = buildExportBlobFilename(studyDto);

            // Google writing happens on this thread
            return saveToGoogleBucket(csvInputStream, fileName, studyDto.getGuid(), bucket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    Runnable buildExportToCsvRunnable(StudyDto studyDto, DataExporter exporter, Writer csvOutputWriter,
                                      List<ActivityExtract> activities,
                                      List<Participant> participants) {
        return () -> {
            try {
                exporter.exportDataSetAsCsv(studyDto, activities, participants, csvOutputWriter);
                // closing here is important! Can't wait until the try block calls close
                csvOutputWriter.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    boolean saveToGoogleBucket(InputStream csvInputStream, String fileName, String studyGuid, Bucket bucket) {
        Blob blob = bucket.create(fileName, csvInputStream, "text/csv");
        LOG.info("Uploaded file {} to bucket {} for study {}", blob.getName(), bucket.getName(), studyGuid);
        return true;
    }

    private String buildExportBlobFilename(StudyDto study) {
        return String.format("%s/%s", study.getName(), DataExporter.makeExportCSVFilename(study.getGuid(), Instant.now()));
    }

    private long estimateExecutionTime(Runnable callback) {
        long start = Instant.now().toEpochMilli();
        callback.run();
        return Instant.now().toEpochMilli() - start;
    }
}
