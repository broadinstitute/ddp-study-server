package org.broadinstitute.ddp.environment;

import java.net.UnknownHostException;


import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.RandomStringUtils;
import spark.utils.StringUtils;

/**
 * Detects Google App Engine instance and service name (for diagnostic/logging purposes basically).
 * It is detected from env variables "GAE_INSTANCE" and "GAE_SERVICE".<br>
 * If GAE_INSTANCE not detected then host value is returned instead (in case of both not detected - fake string is returned).<br>
 * If GAE_SERVICE not detected then fake string is returned.
 */
@Slf4j
public class HostUtil {
    public static final String APPENGINE_INSTANCE_ENV_VAR = "GAE_INSTANCE";
    public static final String APPENGINE_SERVICE_ENV_VAR = "GAE_SERVICE";

    static final String FAKE_VALUE_PREFIX = "unknown-";  // used to generate fixed value if instance/host/service not detected
    static final String FAKE_HOST_TITLE = "host";
    static final String FAKE_GAE_SERVICE_TITLE = "GAE-service";

    private static String GAEInstanceOrHostName;
    private static String GAEServiceName;

    /**
     * If running in app engine, returns GAE instance name.  If a GCP VM, returns the google VM name.
     * Otherwise, returns the host's name.  If host name is not available,
     * returns a fixed string.
     */
    public static synchronized String getGAEInstanceOrHostName()  {
        if (GAEInstanceOrHostName == null) {
            GAEInstanceOrHostName = SystemUtil.getEnv(APPENGINE_INSTANCE_ENV_VAR);
            if (StringUtils.isBlank(GAEInstanceOrHostName)) {
                try {
                    GAEInstanceOrHostName = SystemUtil.getLocalHostName();
                } catch (UnknownHostException hostNameException) {
                    log.warn("Could not resolve hostname", hostNameException);
                    GAEInstanceOrHostName = generateUnknownName(FAKE_HOST_TITLE);
                }
            }
        }
        return GAEInstanceOrHostName;
    }

    /**
     * If running in app engine, returns GAE service name (a service name specified in an app.yaml file).
     * If service name is not available, returns a fixed string.
     */
    public static synchronized String getGAEServiceName()  {
        if (GAEServiceName == null) {
            GAEServiceName = SystemUtil.getEnv(APPENGINE_SERVICE_ENV_VAR);
            if (StringUtils.isBlank(GAEServiceName)) {
                GAEServiceName = generateUnknownName(FAKE_GAE_SERVICE_TITLE);
            }
        }
        return GAEServiceName;
    }

    /**
     * Needed for testing purposes
     */
    @VisibleForTesting
    static synchronized void resetValues() {
        GAEInstanceOrHostName = null;
        GAEServiceName = null;
    }

    /**
     * Generate a fixed name for a serviceName/hostName (in case if not possible to detect real names).
     * Add a random string at the end here to help separate different
     * instances in logging and custom metrics.
     */
    private static String generateUnknownName(String title) {
        return FAKE_VALUE_PREFIX + title + "-" + RandomStringUtils.randomAlphabetic(5).toUpperCase();
    }
}
