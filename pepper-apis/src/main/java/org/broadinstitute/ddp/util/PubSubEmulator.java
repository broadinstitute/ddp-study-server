package org.broadinstitute.ddp.util;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.StartedProcess;

/**
 * Singleton utility for running google's pubsub emulator
 */
public class PubSubEmulator {

    private static final Logger LOG = LoggerFactory.getLogger(PubSubEmulator.class);

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
        emulatorStarted.set(true);
    }

    /**
     * Resets the pubsub emulator so you don't pickup any crufty data from a previous run
     */
    private static void resetViaRest(String pubsubBaseUrl) {
        int shutdownCode = -1;
        try {
            Response shutdownResponse = Request.Post(pubsubBaseUrl + "/reset").execute();
            shutdownCode = shutdownResponse.returnResponse().getStatusLine().getStatusCode();
        } catch (IOException e) {
            throw new RuntimeException("Error while resetting pubsub emulator", e);
        }
        if (shutdownCode != 200) {
            throw new RuntimeException("Could not reset pubsub server.  It returned " + shutdownCode);
        } else {
            LOG.info("Successfully reset pubsub server");
        }
    }

    /**
     * Shuts down the pubsub emulator
     */
    public static void shutdown() throws IOException {
    }

    public static synchronized boolean hasStarted() {
        return pubsubProcess != null;
    }
}
