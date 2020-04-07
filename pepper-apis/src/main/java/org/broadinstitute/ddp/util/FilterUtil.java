package org.broadinstitute.ddp.util;

import static java.util.stream.Collectors.toList;
import static spark.Spark.before;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.broadinstitute.ddp.filter.ExcludePathFilterWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Filter;

public class FilterUtil {
    private static final Logger LOG = LoggerFactory.getLogger(FilterUtil.class);

    /**
     * Creates "before" filter on specified that checks that IP address matches one of those provided
     * Filter will return a 404 and a page not found API error message
     * @param path the URL path
     * @param ips Collection of IPs to allow
     */
    public static void whitelist(String path, Collection<String> ips) {
        Set<String> ipSet = (ips instanceof Set) ? (Set<String>) ips : new HashSet<>(ips);
        before(path, (request, response) -> {
            if (!(ipSet.contains(request.ip()))) {
                List<String> headerString = request.headers().stream()
                        .map(headerName -> headerName + ": " + request.headers(headerName))
                        .collect(toList());
                LOG.warn("Unauthorized IP address tried to access service.\nIP: {}\nURL: {}\nMethod: {}\nBody: {}\nHeaders: {}",
                        request.ip(), request.url(), request.requestMethod(), request.body(), headerString);
                throw ResponseUtil.halt404PageNotFound(response);
            }
        });
    }

    /**
     * Allow to specify the exclusion of a path from the execution of a filter
     *
     * @param filterPath     the path for which the filter is applicable
     * @param pathsToExclude the paths to exclude the execution of the filter
     * @param filter         the filter
     */
    public static void beforeWithExclusion(String filterPath, String[] pathsToExclude, Filter filter) {
        before(filterPath, new ExcludePathFilterWrapper(filter, Arrays.asList(pathsToExclude)));
    }
}
