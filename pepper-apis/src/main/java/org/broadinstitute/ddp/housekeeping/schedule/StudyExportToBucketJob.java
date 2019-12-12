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
import java.util.List;
import java.util.TimeZone;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.export.DataExporter;
import org.broadinstitute.ddp.monitoring.PointsReducerFactory;
import org.broadinstitute.ddp.monitoring.StackdriverCustomMetric;
import org.broadinstitute.ddp.monitoring.StackdriverMetricsTracker;
import org.broadinstitute.ddp.util.GoogleCredentialUtil;
import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
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

public class StudyExportToBucketJob implements Job {

    private static final int READER_BUFFER_SIZE_IN_BYTES = 10 * 1024;
    private static final String DATA_EXPORTER = "exporter";
    private static final String DATA_CREDENTIALS = "credentials";
    private static final String DATA_GOOGLE_PROJECT_ID = "googleProjectId";
    private static final String DATA_GOOGLE_BUCKET_NAME = "googleBucketName";
    private static final String DATA_STUDY_GUID = "studyGuid";

    private static final Logger LOG = LoggerFactory.getLogger(StudyExportToBucketJob.class);

    public static JobKey getKey() {
        return Keys.Export.StudyJob;
    }

    public static void register(Scheduler scheduler, Config cfg) throws SchedulerException {
        String exportSchedule = cfg.getString(ConfigFile.STUDY_EXPORT_SCHEDULE);
        if (exportSchedule.equalsIgnoreCase("off")) {
            LOG.warn("Job '{}' is set to be turned off", getKey());
            return;
        }

        GoogleCredentials credentials = GoogleCredentialUtil
                .initCredentials(cfg.getBoolean(ConfigFile.REQUIRE_DEFAULT_GCP_CREDENTIALS));

        DataExporter exporter = new DataExporter(cfg);

        JobDataMap map = new JobDataMap();
        map.put(DATA_EXPORTER, exporter);
        map.put(DATA_CREDENTIALS, credentials);
        map.put(DATA_GOOGLE_PROJECT_ID, cfg.getString(ConfigFile.GOOGLE_PROJECT_ID));
        map.put(DATA_GOOGLE_BUCKET_NAME, cfg.getString(ConfigFile.STUDY_EXPORT_BUCKET));

        JobDetail job = JobBuilder.newJob(StudyExportToBucketJob.class)
                .withIdentity(getKey())
                .usingJobData(map)
                .requestRecovery(false)
                .storeDurably(true)
                .build();
        scheduler.addJob(job, true);
        LOG.info("Added job '{}' to scheduler", getKey());

        List<StudyDto> studies = TransactionWrapper.withTxn(TransactionWrapper.DB.APIS,
                handle -> handle.attach(JdbiUmbrellaStudy.class).findAll());
        LOG.info("Will schedule export jobs for {} studies", studies.size());

        for (StudyDto studyDto : studies) {
            JobDataMap triggerData = new JobDataMap();
            triggerData.put(DATA_STUDY_GUID, studyDto.getGuid());

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(studyDto.getGuid(), Keys.Export.StudyTriggerGroup)
                    .forJob(getKey())
                    .usingJobData(triggerData)
                    .withSchedule(CronScheduleBuilder
                            .cronSchedule(exportSchedule)
                            .inTimeZone(TimeZone.getTimeZone(ZoneId.of("UTC")))
                            .withMisfireHandlingInstructionFireAndProceed())    // If missed or late, just fire it.
                    .startNow()
                    .build();

            scheduler.scheduleJob(trigger);
            LOG.info("Added trigger '{}' for job '{}' with schedule '{}'", trigger.getKey(), getKey(), exportSchedule);
        }
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap jobDataMap = context.getMergedJobDataMap();
        String studyGuid = jobDataMap.getString(DATA_STUDY_GUID);

        try {
            StudyDto studyDto = TransactionWrapper.withTxn(TransactionWrapper.DB.APIS,
                    handle -> handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid));
            if (!studyDto.isDataExportEnabled()) {
                LOG.warn("Study '{}' does not have data export enabled, skipping job '{}'", studyGuid, getKey());
                return;
            }

            DataExporter exporter = (DataExporter) jobDataMap.get(DATA_EXPORTER);
            String googleProjectId = jobDataMap.getString(DATA_GOOGLE_PROJECT_ID);
            String googleBuckeName = jobDataMap.getString(DATA_GOOGLE_BUCKET_NAME);

            GoogleCredentials googleCredentials = (GoogleCredentials)jobDataMap.get(DATA_CREDENTIALS);
            if (googleCredentials == null) {
                LOG.error("No Google credentials are provided, skipping job '{}' for study '{}'", getKey(), studyGuid);
                return;
            }

            LOG.info("Running job '{}' for study '{}'", getKey(), studyGuid);
            long start = System.currentTimeMillis();
            boolean success = exportStudyToGoogleBucket(studyDto, exporter, googleProjectId, googleBuckeName, googleCredentials);
            long elapsed = System.currentTimeMillis() - start;
            LOG.info("Finished job '{}' for study '{}'. Took {}ms", getKey(), studyGuid, elapsed);

            if (success) {
                new StackdriverMetricsTracker(StackdriverCustomMetric.DATA_EXPORTS,
                        studyGuid,
                        PointsReducerFactory.buildSumReducer()).addPoint(1, Instant.now().toEpochMilli());
            }

        } catch (Exception e) {
            LOG.error("Error while executing job '{}' for study '{}'", getKey(), studyGuid, e);
            throw new JobExecutionException(e, false);
        }
    }

    boolean exportStudyToGoogleBucket(StudyDto studyDto, DataExporter exporter, String googleProjectId, String googleBucketName,
                                           GoogleCredentials googleCredentials) throws IOException {
        try (
                PipedOutputStream outputStream = new PipedOutputStream();
                Writer csvWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
                PipedInputStream csvInputStream = new PipedInputStream(outputStream, READER_BUFFER_SIZE_IN_BYTES);
        ) {

            // Running the DataExporter in separate thread
            Runnable csvExportRunnable = buildExportToCsvRunnable(studyDto, exporter, csvWriter);
            Thread csvExportThread = new Thread(csvExportRunnable);
            csvExportThread.start();

            String fileName = buildExportBlobFilename(studyDto);

            // Google writing happens on this thread
            return saveToGoogleBucket(csvInputStream, fileName, studyDto.getGuid(), googleProjectId, googleBucketName,
                    googleCredentials);
        }
    }

    Runnable buildExportToCsvRunnable(StudyDto studyDto, DataExporter exporter, Writer csvOutputWriter) {
        return () -> {
            try {
                TransactionWrapper.useTxn(TransactionWrapper.DB.APIS,
                        handle -> exporter.exportCsvToOutput(handle, studyDto, csvOutputWriter));
                // closing here is important! Can't wait until the try block calls close
                csvOutputWriter.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    boolean saveToGoogleBucket(InputStream csvInputStream, String fileName, String studyGuid, String googleProjectId,
                                    String googleBucketName,
                                    GoogleCredentials creds) {
        Bucket bucket = getGoogleBucket(googleBucketName, googleProjectId, creds);
        if (bucket == null) {
            LOG.error("No Google credentials are provided, skipping job '{}' for study '{}'", getKey(), studyGuid);
            return false;
        }
        Blob blob = bucket.create(fileName, csvInputStream, "text/csv");
        LOG.info("Uploaded file '{}' to bucket '{}' for study '{}'", blob.getName(), bucket.getName(), studyGuid);
        return true;
    }

    private Bucket getGoogleBucket(String googleBucketName, String googleProjectId, GoogleCredentials creds) {
        Storage storage = StorageOptions.newBuilder()
                .setCredentials(creds)
                .setProjectId(googleProjectId)
                .build()
                .getService();

        return storage.get(googleBucketName);
    }

    private String buildExportBlobFilename(StudyDto study) {
        return String.format("%s/%s", study.getName(), DataExporter.makeExportCSVFilename(study.getGuid(), Instant.now()));
    }
}
