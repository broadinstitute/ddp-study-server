package org.broadinstitute.ddp.cf;

import java.io.IOException;
import java.nio.channels.Channels;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.sendgrid.Attachments;
import com.sendgrid.Email;
import com.sendgrid.Mail;
import com.sendgrid.Method;
import com.sendgrid.Personalization;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;

public class ATCPContactEmailer implements BackgroundFunction<ATCPContactEmailer.FileScanResult> {

    private static final Logger logger = Logger.getLogger(ATCPContactEmailer.class.getName());

    private static final String ATTR_BUCKET_ID = "bucketId";
    private static final String ATTR_OBJECT_ID = "objectId";
    private static final String ATTR_SCAN_RESULT = "scanResult";

    private static final Pattern FILENAME_PATTERN = Pattern.compile("([A-Z0-9]{10}).pdf");
    private static final String ATTACHMENT_DISPOSITION = "attachment";
    private static final String ATTACHMENT_PDF_TYPE = "application/pdf";
    private static final String PATH_MAIL_SEND = "mail/send";

    private final String sendgridFromEmail;
    private final String sendgridFromName;
    private final String sendgridToEmail;
    private final String expectedBucket;
    private final String templateId;

    private final SendGrid sendgrid;
    private final Storage storage;

    public ATCPContactEmailer() {
        String sendgridApiKey = getEnvOrThrow("SENDGRID_API_KEY");
        sendgridFromName = getEnvOrThrow("SENDGRID_FROM_NAME");
        sendgridFromEmail = getEnvOrThrow("SENDGRID_FROM_EMAIL");
        sendgridToEmail = getEnvOrThrow("SENDGRID_TO_EMAIL");
        templateId = getEnvOrThrow("EMAIL_TEMPLATE");
        expectedBucket = getEnvOrThrow("BUCKET");
        String gcpProject = getEnvOrThrow("GCP_PROJECT");

        GoogleCredentials googleCredentials;
        try {
            googleCredentials = GoogleCredentials.getApplicationDefault();
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot obtain GoogleCredentials", e);
        }

        storage = StorageOptions.newBuilder()
                .setCredentials(googleCredentials)
                .setProjectId(gcpProject)
                .build()
                .getService();

        this.sendgrid = new SendGrid(sendgridApiKey);
    }

    private String getEnvOrThrow(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing environment variable: " + name);
        }
        return value;
    }

    @Override
    public void accept(FileScanResult event, Context context) {
        logger.info("Received message: eventId=" + context.eventId() + " timestamp=" + context.timestamp());

        String bucketName = event.getAttributes().get(ATTR_BUCKET_ID);
        String filename = event.getAttributes().get(ATTR_OBJECT_ID);
        String scanResult = event.getAttributes().get(ATTR_SCAN_RESULT);
        logger.info(String.format("File scan result: bucket=%s file=%s result=%s", bucketName, filename, scanResult));

        if (bucketName == null || bucketName.isBlank()) {
            logger.severe("Event message is missing name of bucket");
            return;
        } else if (filename == null || filename.isBlank()) {
            logger.severe("Event message is missing name of file");
            return;
        } else if (!expectedBucket.equals(bucketName)) {
            logger.severe("Event message is not for expected bucket: " + expectedBucket);
            return;
        } else if (!FILENAME_PATTERN.matcher(filename).matches()) {
            // It might be the metadata file, so skip.
            logger.info("Filename pattern does not match, skipping");
            return;
        }

        ScanResult result;
        try {
            result = ScanResult.valueOf(scanResult);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected file scan result: " + scanResult, e);
        }

        Blob contentBlob = storage.get(bucketName, filename);
        Blob metadataBlob = storage.get(bucketName, FILENAME_PATTERN.matcher(filename).replaceAll("$1.metadata"));
        BlobId contentBlobId = contentBlob.getBlobId();
        BlobId metadataBlobId = metadataBlob.getBlobId();

        if (result == ScanResult.INFECTED) {
            // Delete the original file and report the incident.
            storage.delete(contentBlobId);
            String msg = String.format(
                    "File %s is infected! Will not send ATCP DAR email! See metadata file %s for details.",
                    contentBlobId, metadataBlobId);
            throw new RuntimeException(msg);
        }

        Map<String, String> metadata = metadataBlob.getMetadata();
        String originalFilename = metadata.get("meta_filename");

        Long expectedFileSize = Long.valueOf(metadata.get("meta_filesize"));
        Long actualFileSize = contentBlob.getSize();
        if (!actualFileSize.equals(expectedFileSize)) {
            throw new RuntimeException("File size mismatch, expected " + expectedBucket + " but got " + actualFileSize);
        }

        Mail mail = new Mail();
        Email fromEmail = new Email(this.sendgridFromEmail, sendgridFromName);
        mail.setFrom(fromEmail);
        mail.setTemplateId(templateId);

        mail.addAttachments(new Attachments.Builder(originalFilename,
                Channels.newInputStream(contentBlob.reader()))
                            .withDisposition(ATTACHMENT_DISPOSITION)
                            .withType(ATTACHMENT_PDF_TYPE)
                            .build());

        Personalization personalization = new Personalization();
        Email toEmail = new Email(this.sendgridToEmail, this.sendgridToEmail);
        personalization.addTo(toEmail);
        metadata.forEach(personalization::addDynamicTemplateData);
        mail.addPersonalization(personalization);

        int statusCode = sendMail(mail);
        if (statusCode == 200 || statusCode == 202) {
            logger.info("Sent DAR form (status " + statusCode + ") with attachment " + filename);
        } else {
            throw new RuntimeException("Failed to send DAR form (status " + statusCode + ") with attachment " + filename);
        }

        storage.delete(contentBlobId);
        storage.delete(metadataBlobId);
    }

    public int sendMail(Mail mail) {
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint(PATH_MAIL_SEND);
        try {
            request.setBody(mail.build());
            Response response = sendgrid.api(request);
            return response.getStatusCode();
        } catch (IOException e) {
            throw new RuntimeException("Error while sending email", e);
        }
    }

    public enum ScanResult {
        CLEAN,
        INFECTED,
    }

    // Result pubsub message from cf-file-scanner service.
    public static class FileScanResult {

        private String data;
        private Map<String, String> attributes = new HashMap<>();

        public String getData() {
            return data;
        }

        public Map<String, String> getAttributes() {
            return attributes;
        }
    }
}
