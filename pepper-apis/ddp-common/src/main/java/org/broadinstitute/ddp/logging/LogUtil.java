package org.broadinstitute.ddp.logging;

import org.slf4j.MDC;

public class LogUtil {
    public static final String SECURE_LOGGER = "DDP_SECURE";

    // app engin env var for deployment id https://cloud.google.com/appengine/docs/standard/java-gen2/runtime#java_releases
    public static final String GAE_DEPLOYMENT_ID = "GAE_DEPLOYMENT_ID";

    // app engine env var for instance id https://cloud.google.com/appengine/docs/standard/java-gen2/runtime#java_releases
    public static final String GAE_INSTANCE = "GAE_INSTANCE";

    /**
     * Adds some app engine environment variables to {@link MDC}
     * so that they can be referenced in
     * @see <a href="https://logback.qos.ch/manual/mdc.html">logback</a>
     */
    public static void addAppEngineEnvVarsToMDC() {
        MDC.put(GAE_DEPLOYMENT_ID, System.getProperty(GAE_DEPLOYMENT_ID));
        MDC.put(GAE_INSTANCE, System.getProperty(GAE_INSTANCE));
    }

    /**
     * Returns the instance id as set by the
     * app engine environment.
     */
    public static String getAppEngineInstance() {
        return System.getenv(GAE_INSTANCE);
    }
}
