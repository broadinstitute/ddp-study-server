package org.broadinstitute.ddp.util;

import static spark.Spark.before;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.broadinstitute.ddp.filter.ExcludePathFilterWrapper;
import spark.Filter;

public class FilterUtil {
    public static void whitelist(String path, Collection<String> ips) {
        Set<String> ipSet = (ips instanceof Set) ? (Set<String>)ips : new HashSet<>(ips);
        before(path, (request, response) -> {
            if(!(ipSet.contains(request.ip()))) {
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
