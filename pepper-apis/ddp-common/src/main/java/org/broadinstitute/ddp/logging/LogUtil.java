package org.broadinstitute.ddp.logging;

import org.slf4j.MDC;

public class LogUtil {
    public static final String SECURE_LOGGER = "DDP_SECURE";

    // app engin env var for deployment id https://cloud.google.com/appengine/docs/standard/java-gen2/runtime#java_releases
    public static final String GAE_DEPLOYMENT_ID = "GAE_DEPLOYMENT_ID";

    // app engine env var for instance id https://cloud.google.com/appengine/docs/standard/java-gen2/runtime#java_releases
    public static final String GAE_INSTANCE = "GAE_INSTANCE";

    public static void addAppEngineEnvVarsToMDC() {
        MDC.put(GAE_DEPLOYMENT_ID, System.getProperty(GAE_DEPLOYMENT_ID));
        MDC.put(GAE_INSTANCE, System.getProperty(GAE_INSTANCE));
    }

    public static String getAppEngineInstance() {
        return System.getenv(GAE_INSTANCE);
    }
}
