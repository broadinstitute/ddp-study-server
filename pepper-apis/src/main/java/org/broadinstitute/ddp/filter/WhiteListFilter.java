package org.broadinstitute.ddp.filter;

import static java.util.stream.Collectors.toList;
import static spark.Spark.before;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.broadinstitute.ddp.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Filter;
import spark.Request;
import spark.Response;

public class WhiteListFilter implements Filter {
    private static final Logger LOG = LoggerFactory.getLogger(WhiteListFilter.class);
    private Set<String> ipSet;

    public static void whitelist(String path, Collection<String> ips) {
        WhiteListFilter filter = new WhiteListFilter(ips);
        before(path, (request, response) -> {
            filter.handle(request, response);
        });
    }

    /**
     * Creates "before" filter on specified that checks that IP address matches one of those provided
     * Filter will return a 404 and a page not found API error message
     *
     * @param ips  Collection of IPs to allow
     */
    public WhiteListFilter(Collection<String> ips) {
        this.ipSet = (ips instanceof Set) ? (Set<String>) ips : Set.copyOf(ips);
    }

    @Override
    public void handle(Request request, Response response) throws Exception {
        if (!(ipSet.contains(request.ip()))) {
            List<String> headerString = request.headers().stream()
                    .map(headerName -> headerName + ": " + request.headers(headerName))
                    .collect(toList());
            LOG.warn("Unauthorized IP address tried to access service.\nIP: {}\nURL: {}\nMethod: {}\nBody: {}\nHeaders: {}",
                    request.ip(), request.url(), request.requestMethod(), request.body(), headerString);
            throw ResponseUtil.halt404PageNotFound(response);
        }
    }
}
