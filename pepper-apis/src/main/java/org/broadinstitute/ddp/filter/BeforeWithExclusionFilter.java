package org.broadinstitute.ddp.filter;

import static spark.Spark.before;

import java.util.List;

import spark.Filter;

/**
 * Extension to Spark for when you want to specify a filter path but want to exclude one or more paths
 * that are a subset
 */
public class BeforeWithExclusionFilter {
    /**
     * Allow to specify the exclusion paths from the execution of a filter
     *
     * @param filterPath     the path for which the filter is applicable
     * @param pathsToExclude the paths to exclude the execution of the filter
     * @param filter         the filter
     */
    public static void beforeWithExclusion(String filterPath, List<String> pathsToExclude, Filter filter) {
        before(filterPath, new ExcludePathFilterWrapper(filter, pathsToExclude));
    }

    /**
     * Allow to specify the exclusion of a specific path from the execution of a filter
     *
     * @param filterPath     the path for which the filter is applicable
     * @param pathToExclude the paths to exclude the execution of the filter
     * @param filter         the filter
     */
    public static void beforeWithExclusion(String filterPath, String pathToExclude, Filter filter) {
        beforeWithExclusion(filterPath, List.of(pathToExclude), filter);
    }

}
