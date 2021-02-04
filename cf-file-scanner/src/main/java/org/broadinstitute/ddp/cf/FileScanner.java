package org.broadinstitute.ddp.cf;

import java.io.File;
import java.io.IOException;
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
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class FileScanner implements BackgroundFunction<FileScanner.Message> {

    private static final Logger logger = Logger.getLogger(FileScanner.class.getName());

    private static final String APP_CONF = "./application.conf";
    private static final String ENV_CONF_FILE = "config.file";
    private static final String CFG_REFRESH_TIME = "refreshTime";
    private static final String CFG_REFRESH_UNIT = "refreshUnit";
    private static final String CFG_GCP_PROJECT_ID = "gcpProjectId";
    private static final String CFG_RESULT_TOPIC = "resultTopic";

    private static final String ATTR_BUCKET_ID = "bucketId";
    private static final String ATTR_OBJECT_ID = "objectId";
    private static final String ATTR_SCAN_RESULT = "scanResult";

    private static final String FRESHCLAM_CONF = "./freshclam.conf";
    private static final String FRESHCLAM_BIN = "./clamav/freshclam";
    private static final String CLAMSCAN_BIN = "./clamav/clamscan";
    private static final String BIN_DIR = "./clamav";
    private static final String DB_DIR = "/tmp/clamav-db";

    private static final long refreshMillis;
    private static final Storage storage;
    private static final Publisher publisher;
    private static Instant dbUpdatedAt = null;

    // This is ran once on cold start.
    static {
        Config cfg;
        if (System.getenv(ENV_CONF_FILE) != null) {
            cfg = ConfigFactory.load();
        } else {
            cfg = ConfigFactory.parseFile(new File(APP_CONF));
        }

        TimeUnit unit = TimeUnit.valueOf(cfg.getString(CFG_REFRESH_UNIT));
        if (unit == TimeUnit.MICROSECONDS || unit == TimeUnit.NANOSECONDS) {
            throw new RuntimeException("Refresh time unit is too granular: " + unit);
        }
        long refreshTime = cfg.getLong(CFG_REFRESH_TIME);
        refreshMillis = unit.toMillis(refreshTime);

        String gcpProjectId = cfg.getString(CFG_GCP_PROJECT_ID);
        String resultTopic = cfg.getString(CFG_RESULT_TOPIC);
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

        // Pre-load the database files.
        checkDatabaseFiles();
    }

    // This method needs to be thread-safe since it uses shared global state and is used in `accept()`.
    private static synchronized void checkDatabaseFiles() {
        if (dbUpdatedAt == null || Instant.now().toEpochMilli() - dbUpdatedAt.toEpochMilli() >= refreshMillis) {
            var dir = new File(DB_DIR);
            if (!dir.exists() || !dir.isDirectory()) {
                boolean created = dir.mkdir();
                if (!created) {
                    throw new RuntimeException("Could not create database directory: " + DB_DIR);
                }
            }
            runFreshclam();
        }
    }

    private static CommandResult runCommand(boolean withInput, byte[] input, String... command) {
        var builder = new ProcessBuilder(command);
        builder.environment().put("LD_LIBRARY_PATH", BIN_DIR);
        builder.redirectErrorStream(true);
        logger.info("Running command: " + String.join(" ", command));

        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new RuntimeException("Error starting command: " + command[0], e);
        }

        if (withInput && input != null) {
            try {
                process.getOutputStream().write(input);
                process.getOutputStream().flush();
                process.getOutputStream().close();
            } catch (IOException e) {
                throw new RuntimeException("Error writing input to command: " + command[0], e);
            }
        }

        String output;
        try {
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Error reading output from command: " + command[0], e);
        }

        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException("Error waiting for command to finish: " + command[0], e);
        }

        return new CommandResult(output, exitCode);
    }

    private static void runFreshclam() {
        long start = System.currentTimeMillis();
        CommandResult result = runCommand(false, null,
                FRESHCLAM_BIN,
                "--config-file=" + FRESHCLAM_CONF,
                "--datadir=" + DB_DIR);
        logger.info("Output: " + result.getOutput());
        logger.info("Status: " + result.getExitCode());

        int status = result.getExitCode();
        if (status == 0) {
            dbUpdatedAt = Instant.now();
            long elapsed = System.currentTimeMillis() - start;
            logger.info("Finished refreshing the database at: " + dbUpdatedAt + " (took " + elapsed + "ms)");
        } else {
            throw new RuntimeException("Unable to refresh database, freshclam exited with status: " + status);
        }
    }

    private static ScanResult runClamscan(byte[] content) {
        CommandResult result = runCommand(true, content,
                CLAMSCAN_BIN,
                "--database=" + DB_DIR,
                "-");   // Dash means to read from stdin.
        logger.info("Output: " + result.getOutput());
        logger.info("Status: " + result.getExitCode());

        int status = result.getExitCode();
        if (status == 0) {
            return ScanResult.CLEAN;
        } else if (status == 1) {
            return ScanResult.INFECTED;
        } else {
            throw new RuntimeException("Error while scanning file, clamscan exited with status: " + status);
        }
    }

    @Override
    public void accept(Message message, Context context) {
        logger.info("Received message: eventId=" + context.eventId() + " timestamp=" + context.timestamp());

        String bucketName = message.getAttributes().get(ATTR_BUCKET_ID);
        String fileName = message.getAttributes().get(ATTR_OBJECT_ID);
        if (bucketName == null || bucketName.isBlank()) {
            logger.severe("Bucket name is missing in message");
            return;
        } else if (fileName == null || fileName.isBlank()) {
            logger.severe("Bucket filename is missing in message");
            return;
        }

        BlobId blobId = BlobId.of(bucketName, fileName);
        logger.info("Looking up file: " + blobId.toString());
        Blob blob = storage.get(blobId);
        if (blob == null || !blob.exists()) {
            logger.severe("Could not find file: " + blobId.toString());
            // This might be a duplicate (due to pubsub at-least-once delivery), in which case the
            // file might have already been moved. Exit gracefully so we don't incur a cold start.
            return;
        }

        // Ensure database is up-to-date before scanning.
        checkDatabaseFiles();

        logger.info("Scanning file: " + blobId.toString());
        ScanResult result = runClamscan(blob.getContent());

        String data = message.getData() != null ? message.getData() : "";
        var resultMessage = PubsubMessage.newBuilder()
                .putAllAttributes(message.getAttributes())
                .putAttributes(ATTR_SCAN_RESULT, result.name())
                .setData(ByteString.copyFromUtf8(data))
                .build();

        try {
            String msgId = publisher.publish(resultMessage).get();
            logger.info("Published scan result with messageId: " + msgId);
        } catch (InterruptedException|ExecutionException e) {
            throw new RuntimeException("Error publishing scan result for file: " + blobId.toString(), e);
        }
    }

    private enum ScanResult {
        CLEAN,
        INFECTED,
    }

    private static class CommandResult {
        private String output;
        private int exitCode;

        public CommandResult(String output, int exitCode) {
            this.output = output;
            this.exitCode = exitCode;
        }

        public String getOutput() {
            return output;
        }

        public int getExitCode() {
            return exitCode;
        }
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
    public static class Message {

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
