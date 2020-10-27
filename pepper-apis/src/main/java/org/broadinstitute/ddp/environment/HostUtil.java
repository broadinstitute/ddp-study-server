package org.broadinstitute.ddp.environment;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.commons.lang.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.utils.StringUtils;

public class HostUtil {

    private static final Logger LOG = LoggerFactory.getLogger(HostUtil.class);

    public static final String APPENGINE_INSTANCE_ENV_VAR = "GAE_INSTANCE";

    private static String hostName;

    /**
     * If running in app engine, returns the instance name.  If a GCP VM, returns the google VM name.
     * Otherwise, returns the host's name.  If host name is not available,
     * returns a fixed string.
     */
    public static synchronized String getHostName()  {
        if (hostName == null) {
            hostName = System.getenv(APPENGINE_INSTANCE_ENV_VAR);
            if (StringUtils.isBlank(hostName)) {
                try {
                    hostName = InetAddress.getLocalHost().getHostName();
                } catch (UnknownHostException hostNameException) {
                    LOG.warn("Could not resolve hostname", hostNameException);
                    // add a random string at the end here to help separate different instances in logging and custom metrics
                    hostName = "unknown-" + RandomStringUtils.randomAscii(5);
                }
            }
        }
        return hostName;
    }
}
