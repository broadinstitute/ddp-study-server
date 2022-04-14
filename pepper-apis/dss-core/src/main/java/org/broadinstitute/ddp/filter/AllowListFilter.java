package org.broadinstitute.ddp.filter;

import static java.util.stream.Collectors.toList;
import static spark.Spark.before;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.util.ResponseUtil;
import spark.Filter;
import spark.Request;
import spark.Response;

@Slf4j
public class AllowListFilter implements Filter {
    private final Set<String> ipSet;

    public static void allowlist(String path, Collection<String> ips) {
        AllowListFilter filter = new AllowListFilter(ips);
        before(path, filter::handle);
    }

    /**
     * Creates "before" filter on specified that checks that IP address matches one of those provided
     * Filter will return a 404 and a page not found API error message
     *
     * @param ips  Collection of IPs to allow
     */
    public AllowListFilter(Collection<String> ips) {
        this.ipSet = (ips instanceof Set) ? (Set<String>) ips : Set.copyOf(ips);
    }

    @Override
    public void handle(Request request, Response response) throws Exception {
        if (!(ipSet.contains(request.ip()))) {
            List<String> headerString = request.headers().stream()
                    .map(headerName -> headerName + ": " + request.headers(headerName))
                    .collect(toList());
            log.warn("Unauthorized IP address tried to access service.\nIP: {}\nURL: {}\nMethod: {}\nBody: {}\nHeaders: {}",
                    request.ip(), request.url(), request.requestMethod(), request.body(), headerString);
            throw ResponseUtil.halt404PageNotFound(response);
        }
    }
}
