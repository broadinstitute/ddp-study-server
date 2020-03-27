package org.broadinstitute.ddp.filter;

import static spark.Spark.halt;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;

import org.broadinstitute.ddp.util.RequestUtil;
import spark.Filter;
import spark.Request;
import spark.Response;

/**
 * Filter that only allows calls from the specified
 * IP addresses.
 */
public class IPWhitelistFilter implements Filter {

    private final Set<String> allowedIps = new HashSet<>();

    public IPWhitelistFilter(Set<InetAddress> allowedIps) {
        for (InetAddress allowedIp : allowedIps) {
            this.allowedIps.add(allowedIp.getHostAddress());
        }
    }

    @Override
    public void handle(Request request, Response response) throws Exception {
        RequestUtil.parseClientIpFromHeader(request).ifPresentOrElse(ipAddress -> {
            if (!allowedIps.contains(ipAddress)) {
                // not in the whitelist, so deny access
                throw halt(404);
            }
        },
            () -> {
                // no IP found, so deny access
                throw halt(404);
            }
        );
    }
}
