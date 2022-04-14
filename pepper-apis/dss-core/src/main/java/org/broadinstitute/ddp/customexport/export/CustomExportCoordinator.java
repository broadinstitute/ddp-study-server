package org.broadinstitute.ddp.customexport.export;

import static org.broadinstitute.ddp.export.ExportUtil.DEFAULT_BATCH_SIZE;
import static org.broadinstitute.ddp.export.ExportUtil.READER_BUFFER_SIZE_IN_BYTES;
import static org.broadinstitute.ddp.export.ExportUtil.withAPIsTxn;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.customexport.constants.CustomExportConfigFile;
import org.broadinstitute.ddp.customexport.db.dao.CustomExportDao;
import org.broadinstitute.ddp.customexport.db.dto.CompletedUserDto;
import org.broadinstitute.ddp.customexport.model.CustomExportParticipant;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.SendGridMailUtil;

@Slf4j
public class CustomExportCoordinator {
    private final CustomExporter exporter;
    private Bucket csvBucket;
    private final String customActivity;
    private final String customExportStatus;
    private final Config exportCfg;
    private final Config mainCfg;
    private String fullFileName;
    private long exportLastCompleted;

    public CustomExportCoordinator(Config cfg, Config exportCfg) {
        this.exportCfg = exportCfg;
        this.mainCfg = cfg;
        this.customActivity = exportCfg.getString(CustomExportConfigFile.ACTIVITY);
        this.customExportStatus = exportCfg.getString(CustomExportConfigFile.STATUS);
        this.exporter = new CustomExporter(cfg, exportCfg);
    }

    public CustomExportCoordinator includeCsv(Bucket csvBucket) {
        this.csvBucket = csvBucket;
        return this;
    }

    public boolean exportCustom(StudyDto customDto) {
        //Find out whether there is anything to export
        boolean needExport = withAPIsTxn(handle -> {
            CustomExportDao customExportDao = handle.attach(CustomExportDao.class);
            Instant lastCompleted = customExportDao.getLastCompleted(customDto.getId());
            return customExportDao.needCustomExport(customDto.getId(), customExportStatus, lastCompleted.toEpochMilli(), customActivity);
        });

        if (!needExport) {
            log.info("Skipping custom export: nothing to export");
            sendNotificationEmail(false, true);
            return true;
        }

        // Proceed with the export
        List<CustomActivityExtract> activityExtracts =
                withAPIsTxn(handle -> {
                    List<CustomActivityExtract> extracts = exporter.extractActivity(handle);
                    CustomExporter.computeMaxInstancesSeen(handle, extracts);
                    CustomExporter.computeActivityAttributesSeen(handle, extracts);
                    return extracts;
                });

        boolean runSuccess = runCsvExports(customDto, activityExtracts);
        sendNotificationEmail(!runSuccess, false);
        return runSuccess;
    }

    private void sendNotificationEmail(boolean isFailure, boolean isSkip) {
        Config cfg = exportCfg.getConfig(CustomExportConfigFile.EMAIL);

        String fromName = cfg.getString(CustomExportConfigFile.EMAIL_FROM_NAME);
        String fromEmailAddress = cfg.getString(CustomExportConfigFile.EMAIL_FROM_EMAIL);
        String toName = cfg.getString(CustomExportConfigFile.EMAIL_TO_NAME);
        String toEmailAddress = cfg.getString(CustomExportConfigFile.EMAIL_TO_EMAIL);
        String sendGridApiKey = cfg.getString(CustomExportConfigFile.EMAIL_SENDGRID_TOKEN);
        String templateId;

        Map<String, String> templateVarNameToValue = new HashMap<>();
        String subject;

        if (isFailure) {
            subject = cfg.getString(CustomExportConfigFile.EMAIL_ERROR_SUBJECT);
            templateId = cfg.getString(CustomExportConfigFile.EMAIL_ERROR_TEMPLATE_ID);
        } else if (isSkip) {
            subject = cfg.getString(CustomExportConfigFile.EMAIL_SKIP_SUBJECT);
            templateId = cfg.getString(CustomExportConfigFile.EMAIL_SKIP_TEMPLATE_ID);
        } else {
            subject = cfg.getString(CustomExportConfigFile.EMAIL_SUCCESS_SUBJECT);
            templateId = cfg.getString(CustomExportConfigFile.EMAIL_SUCCESS_TEMPLATE_ID);
            String fileUrl = "https://console.cloud.google.com/storage/browser/" + exportCfg.getString(CustomExportConfigFile.BUCKET_NAME)
                    + "/" + exportCfg.getString(CustomExportConfigFile.FILE_PATH);
            templateVarNameToValue.put("bucketLink", fileUrl);
        }

        String proxy = ConfigUtil.getStrIfPresent(mainCfg, ConfigFile.Sendgrid.PROXY);
        log.info("About to send notification email to {} <{}> with{}", toName, toEmailAddress, null == proxy ? "out proxy" :
                " proxy " + proxy);
        SendGridMailUtil.sendDynamicEmailMessage(fromName, fromEmailAddress, toName, toEmailAddress,  subject, templateId,
                templateVarNameToValue, sendGridApiKey, proxy);
    }

    private boolean runCsvExports(StudyDto studyDto, List<CustomActivityExtract> activities) {
        String studyGuid = studyDto.getGuid();
        try {
            log.info("Running csv export for study {}", studyGuid);
            long start = Instant.now().toEpochMilli();
            withAPIsTxn(handle -> {
                CustomExportDao customExportDao = handle.attach(CustomExportDao.class);

                long lastCompleted = customExportDao.getLastCompleted(studyDto.getId()).toEpochMilli();
                var iterator = new CustomExportPaginatedParticipantIterator(studyDto, DEFAULT_BATCH_SIZE, lastCompleted);
                exportStudyToGoogleBucket(studyDto, exporter, csvBucket, activities, iterator);

                // For custom export, keep track of the most recent survey completion time so we know where to start for next export
                customExportDao.updateLastCompleted(exportLastCompleted, studyDto.getId());

                return true;
            });

            long elapsed = Instant.now().toEpochMilli() - start;
            log.info("Finished csv export for study {} in {}s", studyGuid, elapsed / 1000);
            return true;
        } catch (Exception e) {
            log.error("Error while running csv export for study {}, continuing", studyGuid, e);
            return false;
        }
    }

    private void exportStudyToGoogleBucket(StudyDto studyDto, CustomExporter exporter, Bucket bucket,
                                           List<CustomActivityExtract> activities,
                                           Iterator<CustomExportParticipant> participants) {
        try (
                PipedOutputStream outputStream = new PipedOutputStream();
                Writer csvWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
                PipedInputStream csvInputStream = new PipedInputStream(outputStream, READER_BUFFER_SIZE_IN_BYTES)
        ) {
            // Running the DataExporter in separate thread
            Runnable csvExportRunnable = buildExportToCsvRunnable(studyDto, exporter, csvWriter, activities, participants);
            Thread csvExportThread = new Thread(csvExportRunnable);
            csvExportThread.start();

            buildExportBlobFilename(exportCfg.getString(CustomExportConfigFile.FILE_PATH),
                    exportCfg.getString(CustomExportConfigFile.BASE_FILE_NAME));

            // Google writing happens on this thread
            saveToGoogleBucket(csvInputStream, fullFileName, studyDto.getGuid(), bucket);
        } catch (IOException e) {
            throw new DDPException(e);
        }
    }

    private Runnable buildExportToCsvRunnable(StudyDto studyDto, CustomExporter exporter, Writer csvOutputWriter,
                                              List<CustomActivityExtract> activities,
                                              Iterator<CustomExportParticipant> participants) {
        return () -> {
            try {
                int total = exporter.exportDataSetAsCsv(studyDto, activities, participants, csvOutputWriter);
                log.info("Written {} participants to csv export for study {}", total, studyDto.getGuid());
                // closing here is important! Can't wait until the try block calls close
                csvOutputWriter.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private void buildExportBlobFilename(String filePath, String baseFileName) {
        Instant now = Instant.now();
        String fileName = makeExportCSVFilename(baseFileName, now);
        if (filePath != null && !filePath.isEmpty()) {
            fullFileName =  String.format("%s/%s", filePath, fileName);
        } else {
            fullFileName = String.format("%s", fileName);
        }
    }

    private static String makeExportCSVFilename(String baseFileName, Instant timestamp) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX").withZone(ZoneOffset.UTC);
        return String.format("%s%s.csv", fmt.format(timestamp), baseFileName);
    }

    private void saveToGoogleBucket(InputStream csvInputStream, String fileName, String studyGuid, Bucket bucket) {
        Blob blob = bucket.create(fileName, csvInputStream, "text/csv");
        log.info("Uploaded file {} to bucket {} for study {}", blob.getName(), bucket.getName(), studyGuid);
    }

    private class CustomExportPaginatedParticipantIterator implements Iterator<CustomExportParticipant> {

        private final StudyDto studyDto;
        private final int batchSize;
        private int fetched;
        private boolean exhausted;
        private ArrayDeque<CustomExportParticipant> currentBatch;
        private final long customLastCompletion;

        CustomExportPaginatedParticipantIterator(StudyDto studyDto,
                                                 int batchSize, long customLastCompletion) {
            this.studyDto = studyDto;
            this.batchSize = batchSize;
            this.fetched = 0;
            this.exhausted = false;
            this.currentBatch = null;
            this.customLastCompletion = customLastCompletion;
        }

        @Override
        public boolean hasNext() {
            if (exhausted) {
                return false;
            }

            if (currentBatch == null) {
                int offset = fetched;
                currentBatch = withAPIsTxn(handle -> {
                    List<CompletedUserDto> userIds = handle.attach(CustomExportDao.class).findCustomUserIdsToExport(studyDto.getId(),
                            customExportStatus, customLastCompletion, customActivity, batchSize, offset);
                    if (!userIds.isEmpty()) {
                        exportLastCompleted = userIds.get(userIds.size() - 1).getCompletedTime();
                    }
                    List<CustomExportParticipant> extract = exporter.extractParticipantDataSetByIds(handle, studyDto,
                            userIds.stream().map(CompletedUserDto::getUserId).collect(Collectors.toSet()));
                    return new ArrayDeque<>(extract);
                });
                fetched += currentBatch.size();
                if (currentBatch.isEmpty()) {
                    currentBatch = null;
                    exhausted = true;
                }
            }

            return currentBatch != null && !currentBatch.isEmpty();
        }

        @Override
        public CustomExportParticipant next() {
            if (currentBatch == null || currentBatch.isEmpty()) {
                throw new NoSuchElementException();
            }

            CustomExportParticipant next = currentBatch.remove();
            if (currentBatch.isEmpty()) {
                currentBatch = null;
            }

            return next;
        }

        @Override
        public void remove() {
            // Item is immediately removed from underlying collection in the `next()` call
            // in order to save on memory, so nothing else to do here.
        }
    }
}
