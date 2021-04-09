package org.broadinstitute.ddp.customexport.export;

import static org.broadinstitute.ddp.export.ExportUtil.DEFAULT_BATCH_SIZE;
import static org.broadinstitute.ddp.export.ExportUtil.READER_BUFFER_SIZE_IN_BYTES;
import static org.broadinstitute.ddp.export.ExportUtil.makeExportCSVFilename;
import static org.broadinstitute.ddp.export.ExportUtil.withAPIsTxn;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.client.ApiResult;
import org.broadinstitute.ddp.client.SendGridClient;
import org.broadinstitute.ddp.customexport.constants.CustomExportConfigFile;
import org.broadinstitute.ddp.customexport.db.dao.CustomExportDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.export.ActivityExtract;
import org.broadinstitute.ddp.export.ExportUtil;
import org.broadinstitute.ddp.model.study.Participant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomExportCoordinator {

    private static final Logger LOG = LoggerFactory.getLogger(CustomExportCoordinator.class);

    private CustomExporter exporter;
    private Bucket csvBucket;
    private final String customActivity;
    private final String customExportStatus;
    private final Config exportCfg;
    private String fullFileName;

    public CustomExportCoordinator(Config exportCfg) {
        this.exportCfg = exportCfg;
        this.customActivity = exportCfg.getString(CustomExportConfigFile.ACTIVITY);
        this.customExportStatus = exportCfg.getString(CustomExportConfigFile.STATUS);
        this.exporter = new CustomExporter(exportCfg);
    }

    public CustomExportCoordinator includeCsv(Bucket csvBucket) {
        this.csvBucket = csvBucket;
        return this;
    }

    public boolean exportCustom(StudyDto customDto) {
        SendGridClient client =
                new SendGridClient(exportCfg.getConfig(CustomExportConfigFile.EMAIL)
                        .getString(CustomExportConfigFile.EMAIL_SENDGRID_TOKEN));

        //Find out whether there is anything to export
        boolean needExport = withAPIsTxn(handle -> {
            CustomExportDao customExportDao = handle.attach(CustomExportDao.class);
            long lastCompleted = customExportDao.getLastCompleted(customDto.getId());
            return customExportDao.needCustomExport(customDto.getId(), customExportStatus, lastCompleted, customActivity);
        });

        if (!needExport) {
            LOG.info("Skipping custom export: nothing to export");
            ApiResult res = client.sendMail(createNotificationEmail(false, true));
            if (res.hasThrown()) {
                LOG.error("Sending notification email for study " + customDto.getGuid() + " with skipped custom export failed",
                        res.getThrown());
            }
            return true;
        }

        // Proceed with the export
        List<ActivityExtract> activityExtracts = withAPIsTxn(handle -> exporter.extractCustomActivity(handle));

        withAPIsTxn(handle -> {
            ExportUtil.computeMaxInstancesSeen(handle, activityExtracts);
            ExportUtil.computeActivityAttributesSeen(handle, activityExtracts);
            return null;
        });
        boolean runSuccess = runCsvExports(customDto, activityExtracts);

        if (!runSuccess) {
            ApiResult res = client.sendMail(createNotificationEmail(true, false));
            if (res.hasThrown()) {
                LOG.error("Sending notification email for study " + customDto.getGuid() + " with failed custom export failed",
                        res.getThrown());
            }
            return false;
        }

        ApiResult res = client.sendMail(createNotificationEmail(false, false));
        if (res.hasThrown()) {
            LOG.error("Error while sending custom export email notification", res.getThrown());
            return false;
        }

        return true;
    }

    private Mail createNotificationEmail(boolean isFailure, boolean isSkip) {
        Config cfg = exportCfg.getConfig(CustomExportConfigFile.EMAIL);
        String subject;
        Email fromEmail = new Email(cfg.getString(CustomExportConfigFile.EMAIL_FROM_EMAIL),
                cfg.getString(CustomExportConfigFile.EMAIL_FROM_NAME));
        Email toEmail = new Email(cfg.getString(CustomExportConfigFile.EMAIL_TO_EMAIL),
                cfg.getString(CustomExportConfigFile.EMAIL_TO_NAME));

        List<Content> content = new ArrayList<>();

        if (isFailure) {
            subject = cfg.getString(CustomExportConfigFile.EMAIL_ERROR_SUBJECT);
            content.add(new Content("text/plain", cfg.getString(CustomExportConfigFile.EMAIL_ERROR_CONTENT)));
        } else if (isSkip) {
            subject = cfg.getString(CustomExportConfigFile.EMAIL_SKIP_SUBJECT);
            content.add(new Content("text/plain", cfg.getString(CustomExportConfigFile.EMAIL_SKIP_CONTENT)));
        } else {
            subject = cfg.getString(CustomExportConfigFile.EMAIL_SUCCESS_SUBJECT);
            String fileUrl =
                    "https://console.cloud.google.com/storage/browser/" + exportCfg.getString(CustomExportConfigFile.BUCKET_NAME)
                            + "/" + exportCfg.getString(CustomExportConfigFile.FILE_PATH);
            addSuccessContent(content, cfg.getString(CustomExportConfigFile.EMAIL_SUCCESS_CONTENT), fileUrl);
        }

        return createMail(subject, fromEmail, toEmail, content);
    }

    private void addSuccessContent(List<Content> content, String fullContent, String fileUrl) {
        Content linkContent = new Content("text/html", "<a href=\"" + fileUrl + "\">" + fileUrl + "</a>");
        if (!fullContent.contains("{{bucketLink}}")) {
            content.add(new Content("text/plain", fullContent));
            content.add(linkContent);
        } else {
            int startLinkIndex = fullContent.indexOf("{{bucketLink}}");
            int endLinkIndex = startLinkIndex + "{{bucketLink}}".length();
            if (startLinkIndex > 0) {
                content.add(new Content("text/plain", fullContent.substring(0, startLinkIndex)));
            }
            content.add(linkContent);
            if (endLinkIndex < fullContent.length()) {
                content.add(new Content("text/plain", fullContent.substring(endLinkIndex)));
            }
        }
    }

    private Mail createMail(String subject, Email fromEmail, Email toEmail, List<Content> content) {
        Mail mail = new Mail();
        mail.setFrom(fromEmail);
        mail.setSubject(subject);
        Personalization p = new Personalization();
        p.addTo(toEmail);
        mail.addPersonalization(p);
        for (Content c : content) {
            mail.addContent(c);
        }
        return mail;
    }


    private boolean runCsvExports(StudyDto studyDto, List<ActivityExtract> activities) {
        String studyGuid = studyDto.getGuid();
        try {
            LOG.info("Running csv export for study {}", studyGuid);
            long start = Instant.now().toEpochMilli();
            boolean success = withAPIsTxn(handle -> {
                CustomExportDao customExportDao = handle.attach(CustomExportDao.class);

                long lastCompleted = customExportDao.getLastCompleted(studyDto.getId());
                var iterator = new CustomExportPaginatedParticipantIterator(studyDto, DEFAULT_BATCH_SIZE, lastCompleted);
                exportStudyToGoogleBucket(studyDto, exporter, csvBucket, activities, iterator);

                // For custom export, keep track of the most recent survey completion time so we know where to start for next export
                Optional<Long> optionalLastCompletionTime = customExportDao.getLastCustomCompletionDate(
                        studyDto.getId(), customExportStatus, customActivity);
                long lastCompletionTime = optionalLastCompletionTime.orElse((long)0);
                customExportDao.updateLastCompleted(lastCompletionTime, studyDto.getId());

                return true;
            });

            if (!success) {
                LOG.error("Error while running csv export for study {}", studyGuid);
            }

            long elapsed = Instant.now().toEpochMilli() - start;
            LOG.info("Finished csv export for study {} in {}s", studyGuid, elapsed / 1000);
            return true;
        } catch (Exception e) {
            LOG.error("Error while running csv export for study {}, continuing", studyGuid, e);
            return false;
        }
    }

    private void exportStudyToGoogleBucket(StudyDto studyDto, CustomExporter exporter, Bucket bucket,
                                           List<ActivityExtract> activities,
                                           Iterator<Participant> participants) {
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
                                              List<ActivityExtract> activities,
                                              Iterator<Participant> participants) {
        return () -> {
            try {
                int total = exporter.exportDataSetAsCsv(studyDto, activities, participants, csvOutputWriter);
                LOG.info("Written {} participants to csv export for study {}", total, studyDto.getGuid());
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

    private void saveToGoogleBucket(InputStream csvInputStream, String fileName, String studyGuid, Bucket bucket) {
        Blob blob = bucket.create(fileName, csvInputStream, "text/csv");
        LOG.info("Uploaded file {} to bucket {} for study {}", blob.getName(), bucket.getName(), studyGuid);
    }

    private class CustomExportPaginatedParticipantIterator implements Iterator<Participant> {

        private final StudyDto studyDto;
        private final int batchSize;
        private int fetched;
        private boolean exhausted;
        private ArrayDeque<Participant> currentBatch;
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
                    CustomExportDao export = handle.attach(CustomExportDao.class);
                    Set<Long> userIds = export.findCustomUserIdsToExport(studyDto.getId(), customExportStatus, customLastCompletion,
                            customActivity, batchSize, offset);
                    List<Participant> extract = CustomExporter.extractParticipantDataSetByIds(handle, studyDto, userIds);
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
        public Participant next() {
            if (currentBatch == null || currentBatch.isEmpty()) {
                throw new NoSuchElementException();
            }

            Participant next = currentBatch.remove();
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
