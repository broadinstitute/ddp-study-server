package org.broadinstitute.ddp.filter;

import static spark.Spark.after;
import static spark.Spark.before;

import spark.Filter;

/**
 * Extension to Spark for when you want to specify a filter path but want to exclude one or more paths that are a
 * subset
 */
public class Exclusions {

    /**
     * Allow to specify the exclusion paths from the execution of a before filter
     *
     * @param filterPath     the path for which the filter is applicable
     * @param filter         the filter
     * @param pathsToExclude the paths to exclude the execution of the filter
     */
    public static void beforeWithExclusion(String filterPath, Filter filter, String... pathsToExclude) {
        before(filterPath, new ExcludePathFilter(filter, pathsToExclude));
    }

    /**
     * Allow to specify the exclusion paths from the execution of an after filter
     *
     * @param filterPath     the path for which the filter is applicable
     * @param filter         the filter
     * @param pathsToExclude the paths to exclude the execution of the filter
     */
    public static void afterWithExclusion(String filterPath, Filter filter, String... pathsToExclude) {
        after(filterPath, new ExcludePathFilter(filter, pathsToExclude));
    }
}
