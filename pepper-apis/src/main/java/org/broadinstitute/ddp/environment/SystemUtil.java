package org.broadinstitute.ddp.environment;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Wraps some system methods.
 * One of benefits of this wrapping: to do staticMock in unit tests.
 */
public class SystemUtil {

    public static String getEnv(String varName) {
        return System.getenv(varName);
    }

    public static String getLocalHostName() throws UnknownHostException {
        return InetAddress.getLocalHost().getHostName();
    }
}
