package org.broadinstitute.ddp.event.dsmtask.api;

public class DsmTaskLogUtil {

    private static final String LOG_PREFIX_DSM_TASK = "DSM_TASK";

    public static String infoMsg(String msg) {
        return LOG_PREFIX_DSM_TASK + ": " + msg;
    }

    public static String errorMsg(String msg) {
        return LOG_PREFIX_DSM_TASK + " error: " + msg;
    }
}
