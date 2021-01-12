package org.broadinstitute.ddp.cf;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.sendgrid.*;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.nio.channels.Channels;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class AttachmentLoadedFunction implements BackgroundFunction<AttachmentLoadedFunction.GcsEvent> {
    private static final Logger logger = Logger.getLogger(AttachmentLoadedFunction.class.getName());
    private static final String ATTACHMENT_DISPOSITION = "attachment";
    private static final String ATTACHMENT_PDF_TYPE = "application/pdf";
    public static final String PATH_MAIL_SEND = "mail/send";

    private static final String API_KEY = "SG_KEY";
    private static final String FROM_EMAIL = "FROM_EMAIL";
    private static final String FROM_NAME = "DEV";
    private static final String TO_EMAIL = "TO_EMAIL";
    private static final String TEMPLATE_ID = "d-5ec2c969bbad4fffabb8168abafa470c";
    private static final String GCP_PROJECT = "broad-ddp-dev";

    private final SendGrid sendGrid;
    private final Storage storage;

    public AttachmentLoadedFunction() {
        GoogleCredentials googleCredentials;
        try {
            googleCredentials = GoogleCredentials.getApplicationDefault();
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot obtain GoogleCredentials", e);
        }

        storage = StorageOptions.newBuilder()
                .setCredentials(googleCredentials)
                .setProjectId(GCP_PROJECT)
                .build()
                .getService();

        var httpClientBuilder = HttpClients.custom();
        var client = new Client(httpClientBuilder.build());
        this.sendGrid = new SendGrid(API_KEY, client);
    }

    @Override
    public void accept(GcsEvent event, Context context) {
        String bucketName = event.getBucket();
        String filename = event.getName();
        logger.info(String.format("%s triggered with EventID: %s. File %s created in bucket %s",
                context.eventType(), context.eventId(), filename, bucketName));

        Pattern pattern = Pattern.compile("([^/]+/[A-Z0-9]{10}).pdf");
        if (!pattern.matcher(filename).matches()) {
            logger.info("Filename pattern does not match, skipping");
            return;
        }

        BlobId contentBlobId = BlobId.of(bucketName, filename);
        Blob contentBlob = storage.get(contentBlobId);
        BlobId metadataBlobId = BlobId.of(bucketName, pattern.matcher(filename)
                .replaceAll("$1.metadata"));
        Blob metadataBlob = storage.get(metadataBlobId);
        Map<String, String> metadata = metadataBlob.getMetadata();
        String originalFilename = metadata.get("meta_filename");
        if (!contentBlob.getSize().equals(Long.valueOf(metadata.get("meta_filesize")))) {
            throw new RuntimeException("File size mismatch");
        }

        Mail mail = new Mail();
        Email fromEmail = new Email(FROM_EMAIL, FROM_NAME);
        mail.setFrom(fromEmail);
        mail.setTemplateId(TEMPLATE_ID);

        mail.addAttachments(new Attachments.Builder(originalFilename,
                Channels.newInputStream(contentBlob.reader()))
                            .withDisposition(ATTACHMENT_DISPOSITION)
                            .withType(ATTACHMENT_PDF_TYPE)
                            .build());

        Personalization personalization = new Personalization();
        Email toEmail = new Email(TO_EMAIL, TO_EMAIL);
        personalization.addTo(toEmail);
        metadata.forEach(personalization::addDynamicTemplateData);
        mail.addPersonalization(personalization);

        boolean sendResult = sendMail(mail);
        if (sendResult) {
            logger.info("Sent DAR form with attachment " + filename);
        } else {
            logger.warning("Failed to send DAR form with attachment " + filename);
        }

        storage.delete(contentBlobId);
        storage.delete(metadataBlobId);
    }

    public boolean sendMail(Mail mail) {
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint(PATH_MAIL_SEND);
        try {
            request.setBody(mail.build());
            Response response = sendGrid.api(request);
            int statusCode = response.getStatusCode();
            return statusCode == 200 || statusCode == 202;
        } catch (IOException e) {
            return false;
        }
    }

    static class GcsEvent {
        private String bucket;
        private String name;

        public String getBucket() {
            return bucket;
        }

        public String getName() {
            return name;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
