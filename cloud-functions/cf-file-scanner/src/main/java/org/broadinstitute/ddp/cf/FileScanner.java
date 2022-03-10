package org.broadinstitute.ddp.cf;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.Channels;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
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

    /**
     * the host and port of the clamd server to use for scanning.
     * 
     * <p>the value is expected to be in one of the following formats:
     * <ul>
     *  <li>[host]</li>
     *  <li>[host]:[port]</li>
     * </ul>
     * 
     * <p>where [host] may be an IP address or FQDN.
     */
    private static final String ENV_DDP_CLAMAV_SERVER = "DDP_CLAMAV_SERVER";

    private static final String ATTR_BUCKET_ID = "bucketId";
    private static final String ATTR_OBJECT_ID = "objectId";
    private static final String ATTR_SCAN_RESULT = "scanResult";

    private static final Storage storage;
    private static final Optional<Publisher> publisher;
    private static final InetSocketAddress clamdAddress;

    // This is ran once on cold start.
    static {
        String gcpProjectId = getEnvOrThrow(ENV_GCP_PROJECT);
        String clamavServer = getEnvOrThrow(ENV_DDP_CLAMAV_SERVER);

        /* 
            The topic to be used for publishing scan result events
        */
        String resultTopic = System.getenv(ENV_RESULT_TOPIC);

        try {
            storage = StorageOptions.newBuilder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .setProjectId(gcpProjectId)
                    .build().getService();
        } catch (IOException e) {
            throw new RuntimeException("Error initializing storage service", e);
        }

        /*
            If resultTopic is not set, this function will not publish a message with the results of its scans,
            and execution will not be halted.
        */
        if (resultTopic != null && resultTopic.isBlank() == false) {
            try {
                var topic = ProjectTopicName.of(gcpProjectId, resultTopic);
                publisher = Optional.of(Publisher.newBuilder(topic).build());
            } catch (IOException e) {
                throw new RuntimeException("Error initializing pubsub publisher", e);
            }
        } else {
            publisher = Optional.empty();
        }

        try {
            var clamavUrl = new URI(null, clamavServer, null, null, null).parseServerAuthority();
            clamdAddress = new InetSocketAddress(clamavUrl.getHost(), clamavUrl.getPort());
        } catch (URISyntaxException cause) {
            throw new RuntimeException("env variable " + ENV_DDP_CLAMAV_SERVER + " is not a valid server authority.", cause);
        }
    }

    private static String getEnvOrThrow(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new RuntimeException("Missing value for environment variable: " + name);
        }
        return value;
    }

    /**
     * Run a scan on the content input.
     *
     * <p>This method consumes the contents of the input streams and guarantees that it will be closed upon return.
     * </p>
     *
     * @param content we'll take ownership of this input stream and close it at the end
     * @return whether clean or infected
     */
    private ScanResult runClamscan(Client client, InputStream content) throws IOException {
        var scanResult = client.scan(content);
        
        switch (scanResult.result) {
            case POSITIVE:
                logger.severe("malware variant identified " + scanResult.message.orElse(""));
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
            var logMessage = "message " + messageId + ": "
                                + "Bucket name is missing in message (attribute " + ATTR_BUCKET_ID + ")";
            logger.severe(logMessage);
            throw new RuntimeException(logMessage);
        }
        
        if (fileName == null || fileName.isBlank()) {
            var logMessage = "message " + messageId + ": "
                                + "Bucket filename is missing in message (attribute " + ATTR_OBJECT_ID + ")";
            logger.severe(logMessage);
            throw new RuntimeException(logMessage);
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

        var clamav = new Client(clamdAddress.getHostName(), clamdAddress.getPort());

        ScanResult result;
        try {
            if (clamav.ping() == false) {
                var logMessage = "message " + messageId + ": "
                                    + "clamd host " + clamdAddress.toString() + " did not respond to a PING";
                logger.severe(logMessage);
                throw new RuntimeException(logMessage);
            }

            logger.fine("message " + messageId + ": "
                        + "clamd host " + clamdAddress.toString() + " responded to a PING");

            var bucketDataStream = Channels.newInputStream(blob.reader());
            result = runClamscan(clamav, bucketDataStream);

            switch (result) {
                case INFECTED:
                    logger.severe("message " + messageId + ": "
                                    + "malware was identified in object " + blob.getBucket() + "/" + blob.getName());
                    break;

                case CLEAN:
                    logger.fine("message " + messageId + ": "
                                + "no malware identified in object " + blob.getBucket() + "/" + blob.getName());
                    break;
                default:
                    throw new RuntimeException("unreachable");
            }
        } catch (IOException ioe) {
            throw new RuntimeException("connection with " + clamdAddress.toString() + " was unexpectedly closed", ioe);
        }

        if (publisher.isPresent()) {
            var data = message.getData() != null ? message.getData() : "";
            var resultMessage = PubsubMessage.newBuilder()
                    .putAllAttributes(message.getAttributes())
                    .putAttributes(ATTR_SCAN_RESULT, result.name())
                    .setData(ByteString.copyFromUtf8(data))
                    .build();

            try {
                var msgId = publisher.get().publish(resultMessage).get();
                logger.info("Published scan result with messageId: " + msgId);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Error publishing scan result for file: " + blobId.toString(), e);
            }
        } else {
            logger.info("PubSub topic not configured, not sending a scan result message");
        }
    }

    private enum ScanResult {
        CLEAN,
        INFECTED,
    }

    /**
     * This expects a pubsub notification from Google Cloud Storage. See docs about format here:
     * https://cloud.google.com/storage/docs/pubsub-notifications#format.
     * 
     * <p>The important pieces are the `bucketId` and `objectId` attributes.
     * 
     * <p>This message will be passed along downstream as-is, with an additional attribute for the file
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
