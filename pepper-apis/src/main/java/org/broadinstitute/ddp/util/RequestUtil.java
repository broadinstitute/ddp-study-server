package org.broadinstitute.ddp.util;

import java.net.InetAddress;
import java.util.Optional;

import com.google.common.net.HttpHeaders;
import spark.Request;

public class RequestUtil {
    public static String urlComponentToStringConstant(String urlComponent) {
        return urlComponent.replaceAll("-", "_").toUpperCase();
    }

    /**
     * Attempts to parse the client IP of the request based on headers.
     * @param req the request
     * @return the IP address most likely to be the client, or empty if none is found
     */
    public static Optional<String> parseClientIpFromHeader(Request req)  {
        String forwardedFor = req.headers(HttpHeaders.X_FORWARDED_FOR);
        if (forwardedFor == null) {
            forwardedFor = "";
        }
        String[] clientIps = forwardedFor.split(",");

        if (clientIps.length > 0)  {
            return Optional.of(clientIps[0].replace(",",""));
        } else  {
            return Optional.empty();
        }
    }
}
