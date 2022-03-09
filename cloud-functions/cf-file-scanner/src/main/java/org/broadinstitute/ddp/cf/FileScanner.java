package org.broadinstitute.ddp.cf;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;

import org.broadinstitute.ddp.clamav.Client;

public class FileScanner implements BackgroundFunction<FileScanner.Message> {

    private static final Logger logger = Logger.getLogger(FileScanner.class.getName());

    private static final String ENV_GCP_PROJECT = "GCP_PROJECT";
    private static final String ENV_RESULT_TOPIC = "RESULT_TOPIC";

    private static final String ATTR_BUCKET_ID = "bucketId";
    private static final String ATTR_OBJECT_ID = "objectId";
    private static final String ATTR_SCAN_RESULT = "scanResult";

    private static final Storage storage;
    private static final Publisher publisher;

    // This is ran once on cold start.
    static {
        String gcpProjectId = getEnvOrThrow(ENV_GCP_PROJECT);
        String resultTopic = getEnvOrThrow(ENV_RESULT_TOPIC);
        try {
            storage = StorageOptions.newBuilder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .setProjectId(gcpProjectId)
                    .build().getService();
        } catch (IOException e) {
            throw new RuntimeException("Error initializing storage service", e);
        }

        try {
            var topic = ProjectTopicName.of(gcpProjectId, resultTopic);
            publisher = Publisher.newBuilder(topic).build();
        } catch (IOException e) {
            throw new RuntimeException("Error initializing pubsub publisher", e);
        }
    }

    private static String getEnvOrThrow(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new RuntimeException("Missing environment variable: " + name);
        }
        return value;
    }

    /**
     * Run a scan on the content input.
     * <p>
     * This method consumes the contents of the input streams and guarantees that it will
     * be closed upon return.
     *
     * @param content we'll take ownership of this input stream and close it at the end
     * @return whether clean or infected
     */
    private ScanResult runClamscan(Client client, InputStream content) throws IOException {
        var scanResult = client.scan(content);
        
        switch (scanResult.result) {
            case POSITIVE:
                logger.severe("");
                return ScanResult.INFECTED;

            case NEGATIVE:
                return ScanResult.CLEAN;

            default:
                throw new RuntimeException("unreachable");
        }
    }

    @Override
    public void accept(FileScanner.Message message, Context context) {
        logger.info("Received message: eventId=" + context.eventId() + " timestamp=" + context.timestamp());
        
        final var messageId = context.eventId();

        String bucketName = message.getAttributes().get(ATTR_BUCKET_ID);
        String fileName = message.getAttributes().get(ATTR_OBJECT_ID);
        if (bucketName == null || bucketName.isBlank()) {
            logger.severe("Bucket name is missing in message");
            throw new RuntimeException(String.format("attribute value for key %s is missing or empty", ATTR_BUCKET_ID));
        }
        
        if (fileName == null || fileName.isBlank()) {
            logger.severe("Bucket filename is missing in message");
            throw new RuntimeException(String.format("attribute value for key %s is missing or empty", ATTR_OBJECT_ID));
        }

        logger.info("Looking up file: bucket=" + bucketName + " name=" + fileName);
        Blob blob = storage.get(bucketName, fileName);
        if (blob == null || !blob.exists()) {
            logger.severe("Could not find file: bucket=" + bucketName + " name=" + fileName);
            // This might be a duplicate (due to pubsub at-least-once delivery), in which case the
            // file might have already been moved. Exit gracefully so we don't incur a cold start.
            return;
        }

        BlobId blobId = blob.getBlobId();
        Instant createdAt = Instant.ofEpochMilli(blob.getCreateTime());
        logger.info("Found file that was created at " + createdAt.toString());

        logger.info("Scanning file: " + blobId.toString());

        var clamdHost = "127.0.0.1";
        var clamav = new Client("127.0.0.1", 13310);

        ScanResult result;
        try {
            if (clamav.ping() == false) {
                logger.severe("clamd host not responding to PING");
                throw new RuntimeException(String.format("no response for PING to %s", clamdHost));
            }

             logger.info(String.format("PING to %s was successful", clamdHost));

            // Used for testing purposes. This will be replaced with the bucket input
            // stream for release. This code should be moved to a test suite
            var eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";
            var dataStream = new ByteArrayInputStream(eicar.getBytes(StandardCharsets.US_ASCII));
            var bucketDataStream = Channels.newInputStream(blob.reader());

            result = runClamscan(clamav, bucketDataStream);
            logger.info(String.format("got result %s", result.name()));
        } catch (IOException ioe) {
            throw new RuntimeException(String.format("connection to %s unexpectedly closed", clamdHost), ioe);
        }

        String data = message.getData() != null ? message.getData() : "";
        var resultMessage = PubsubMessage.newBuilder()
                .putAllAttributes(message.getAttributes())
                .putAttributes(ATTR_SCAN_RESULT, result.name())
                .setData(ByteString.copyFromUtf8(data))
                .build();

        try {
            String msgId = publisher.publish(resultMessage).get();
            logger.info("Published scan result with messageId: " + msgId);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error publishing scan result for file: " + blobId.toString(), e);
        }
    }

    private enum ScanResult {
        CLEAN,
        INFECTED,
    }

    /**
     * This expects a pubsub notification from Google Cloud Storage. See docs about format here:
     * https://cloud.google.com/storage/docs/pubsub-notifications#format.
     * <p>
     * The important pieces are the `bucketId` and `objectId` attributes.
     * <p>
     * This message will be passed along downstream as-is, with an additional attribute for the file
     * scan result, so downstream consumers may have more context about the file scanned.
     */
    public class Message {

        private String data;
        private Map<String, String> attributes = new HashMap<>();

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }

        public Map<String, String> getAttributes() {
            return attributes;
        }

        public void setAttributes(Map<String, String> attributes) {
            this.attributes = attributes;
        }
    }
}
