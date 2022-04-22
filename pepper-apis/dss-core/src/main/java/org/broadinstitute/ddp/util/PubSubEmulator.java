package org.broadinstitute.ddp.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.broadinstitute.ddp.exception.DDPException;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.exec.stream.LogOutputStream;

/**
 * Singleton utility for running google's pubsub emulator
 */
@Slf4j
public class PubSubEmulator {
    private static final String STARTED_LOG_MESSAGE = "Server started";

    private static final int PUBSUB_EMULATOR_TIMEOUT_MS = 10 * 1000;
    public static final String PUBSUB_EMULATOR_HOST = "PUBSUB_EMULATOR_HOST";

    private static final AtomicBoolean pubsubMonitor = new AtomicBoolean();

    private static StartedProcess pubsubProcess;

    private static final String EMULATOR_HOST = System.getenv(PUBSUB_EMULATOR_HOST);

    private static final String PUBSUB_BASE_URL = "http://" + EMULATOR_HOST;

    /**
     * Starts the emulator
     *
     * @throws IllegalStateException if the emulator has already been started
     */
    public static synchronized void startEmulator() {
        if (StringUtils.isBlank(EMULATOR_HOST)) {
            throw new RuntimeException("No host:port set for emulator; please set " + PUBSUB_EMULATOR_HOST);
        }
        if (pubsubProcess != null) {
            throw new IllegalArgumentException("Emulator has already been started.");
        }
        final AtomicBoolean emulatorStarted = new AtomicBoolean();
        try {
            Thread emulatorThread = new Thread(() -> {
                try {
                    String tempDir = "ddp-pubsub-testing" + System.currentTimeMillis();
                    Path pubsubDataDir = Files.createTempDirectory(tempDir);
                    pubsubDataDir.toFile().deleteOnExit();
                    log.info("Pubsub emulator will put data in {}", pubsubDataDir.toAbsolutePath());
                    ProcessExecutor exec = new ProcessExecutor().command("gcloud", "beta", "emulators", "pubsub",
                                                                         "start", "--host-port=" + EMULATOR_HOST,
                                                                         "--data-dir=" + pubsubDataDir
                                                                                 .toAbsolutePath())
                            .redirectOutput(new LogOutputStream() {
                                @Override
                                protected void processLine(String line) {
                                    log.info(line);
                                    if (line.contains(STARTED_LOG_MESSAGE)) {
                                        synchronized (pubsubMonitor) {
                                            emulatorStarted.set(true);
                                            pubsubMonitor.notify();
                                        }
                                    }
                                    if (line.contains("Exception")) {
                                        emulatorStarted.set(false);
                                        log.error("Error starting pubsub emulator: " + line);
                                    }
                                }
                            });
                    pubsubProcess = exec.start();
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        try {
                            if (pubsubProcess != null) {
                                shutdown();
                            }
                        } catch (IOException e) {
                            log.error("Error during pubsub shutdown", e);
                        }
                    }));
                    pubsubProcess.getProcess().waitFor();
                } catch (Exception e) {
                    throw new DDPException("Error starting pubsub emulator", e);
                }
            });
            emulatorThread.start();
            synchronized (pubsubMonitor) {
                log.info("Waiting for pubsub emulator to start");
                pubsubMonitor.wait(PUBSUB_EMULATOR_TIMEOUT_MS);
            }
        } catch (Exception e) {
            throw new RuntimeException("Trouble starting pubsub simulator", e);
        }
        if (!emulatorStarted.get()) {
            throw new RuntimeException("Failed to start emulator");
        }
    }

    /**
     * Resets the pubsub emulator so you don't pickup any crufty data from a previous run
     */
    private static void resetViaRest(String pubsubBaseUrl) {
        int shutdownCode;
        try {
            Response shutdownResponse = Request.Post(pubsubBaseUrl + "/reset").execute();
            shutdownCode = shutdownResponse.returnResponse().getStatusLine().getStatusCode();
        } catch (IOException e) {
            throw new RuntimeException("Error while resetting pubsub emulator", e);
        }
        if (shutdownCode != 200) {
            throw new RuntimeException("Could not reset pubsub server.  It returned " + shutdownCode);
        } else {
            log.info("Successfully reset pubsub server");
        }
    }

    /**
     * Shuts down the pubsub emulator
     */
    public static void shutdown() throws IOException {
        resetViaRest(PUBSUB_BASE_URL);
        Response shutdownResponse = Request.Post(PUBSUB_BASE_URL + "/shutdown").execute();
        int shutdownCode = shutdownResponse.returnResponse().getStatusLine().getStatusCode();
        if (shutdownCode != 200) {
            log.error("Pubsub emulator returned {} during shutdown", shutdownCode);
        }
        if (pubsubProcess != null) {
            pubsubProcess.getProcess().destroy();
            try {
                pubsubProcess.getProcess().waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.error("Pubsub emulator shutdown interrupted", e);
            }
        }
    }

    public static synchronized boolean hasStarted() {
        // We might be running a Docker based emulator
        if (isExternalEmulatorRunning()) {
            log.info("An emulator already running at " + EMULATOR_HOST);
            return true;
        } else {
            return pubsubProcess != null;
        }
    }

    private static boolean isExternalEmulatorRunning() {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            return httpClient.execute(new HttpGet(PUBSUB_BASE_URL)).getStatusLine().getStatusCode() == 200;
        } catch (HttpHostConnectException e) {
            log.debug("Could not connect to pubsub server. Must not be running");
            return false;
        } catch (IOException e) {
            String msg = "There was problem initializing CloseableHttpClient";
            log.error(msg, e);
            throw new DDPException(msg, e);
        }
    }
}
