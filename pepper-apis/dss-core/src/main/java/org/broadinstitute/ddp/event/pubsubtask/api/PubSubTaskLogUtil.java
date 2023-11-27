package org.broadinstitute.ddp.event.pubsubtask.api;

/**
 * Adds a prefix to log messages in PubSubTask API.
 */
public class PubSubTaskLogUtil {

    /**
     * This prefix added to every log message of PubSubTask API.
     * And it allows to easily find in logs the log messages related to this API only.
     * Fpr example on Google Cloud logs:
     * <pre>
     *     textPayload: "PUBSUB_TASK"
     * </pre>
     */
    private static final String LOG_PREFIX_PUBSUB_TASK = "PUBSUB_TASK";

    public static String infoMsg(String msg) {
        return LOG_PREFIX_PUBSUB_TASK + ": " + msg;
    }

    public static String errorMsg(String msg) {
        return LOG_PREFIX_PUBSUB_TASK + " error: " + msg;
    }
}
