package org.broadinstitute.ddp.event.pubsubtask.api;

/**
 * Adds a prefix to log messages in PubSubTask API.
 */
public class PubSubTaskLogUtil {

    private static final String LOG_PREFIX_PUBSUB_TASK = "PUBSUB_TASK";

    public static String infoMsg(String msg) {
        return LOG_PREFIX_PUBSUB_TASK + ": " + msg;
    }

    public static String errorMsg(String msg) {
        return LOG_PREFIX_PUBSUB_TASK + " error: " + msg;
    }
}
