package org.broadinstitute.ddp.filter;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import spark.Filter;
import spark.Request;
import spark.Response;
import spark.utils.SparkUtils;

/**
 * Try to fill in what appears to be gap in Spark: ability to exclude execution of a filter
 * for a given pattern. This deals with the situation where you want to apply the filter to
 * a path such as /* <b>EXCEPT </b> /dir1/myExcludedStuff/*
 */
public class ExcludePathFilter implements Filter {
    private Set<String> pathsToExclude = new HashSet<>();
    private Filter wrappedFilter;

    /**
     * Can specify multiple paths to exclude.
     * @param filterToWrap the filter
     * @param pathsToExclude the paths to exclude. Wildcards are fine. Use same notation as Spark.
     */
    public ExcludePathFilter(Filter filterToWrap, String... pathsToExclude) {
        this.wrappedFilter = filterToWrap;
        Collections.addAll(this.pathsToExclude, pathsToExclude);
    }

    /**
     * Can specify multiple paths to exclude.
     * @param filterToWrap the filter
     * @param pathsToExclude the paths to exclude. Wildcards are fine. Use same notation as Spark.
     */
    public ExcludePathFilter(Filter filterToWrap, Collection<String> pathsToExclude) {
        this.wrappedFilter = filterToWrap;
        this.pathsToExclude.addAll(pathsToExclude);
    }

    /**
     * If request path matches one of our excluded patterns, we ignore it
     * otherwise we are happy to execute it.
     * @param request request
     * @param response response
     * @throws Exception exception
     */
    @Override
    public void handle(Request request, Response response) throws Exception {
        if (pathsToExclude.stream().noneMatch(excludedPath -> pathsMatch(excludedPath, request.uri()))) {
            wrappedFilter.handle(request, response);
        }
    }

    /**
     * This implementation of pathsMatch is for the most part reproduced from Spark's implementation.
     * Unfortunately the Spark implementation is buried and private but we try here to have exclusion patterns
     * behave the same way as the path patterns used in Spark.
     * @param firstPath the first path
     * @param secondPath the second path
     * @return whether the second path matches the second.
     */
    private boolean pathsMatch(String firstPath, String secondPath) {
        if (!firstPath.endsWith("*") && ((secondPath.endsWith("/") && !firstPath.endsWith("/"))
                || (firstPath.endsWith("/") && !secondPath.endsWith("/")))) {
            // One and not both ends with slash
            return false;
        }
        if (firstPath.equals(secondPath)) {
            // Paths are the same
            return true;
        }

        // check params
        List<String> firstPathParts = SparkUtils.convertRouteToList(firstPath);
        List<String> secondPathParts = SparkUtils.convertRouteToList(secondPath);

        int thisPathSize = firstPathParts.size();
        int pathSize = secondPathParts.size();

        if (thisPathSize == pathSize) {
            for (int i = 0; i < thisPathSize; i++) {
                String thisPathPart = firstPathParts.get(i);
                String pathPart = secondPathParts.get(i);

                if ((i == thisPathSize - 1) && (thisPathPart.equals("*") && firstPath.endsWith("*"))) {
                    // wildcard match
                    return true;
                }

                if ((!thisPathPart.startsWith(":"))
                        && !thisPathPart.equals(pathPart)
                        && !thisPathPart.equals("*")) {
                    return false;
                }
            }
            // All parts matched
            return true;
        } else {
            // Number of "path parts" not the same
            // check wild card:
            if (firstPath.endsWith("*")) {
                if (pathSize == (thisPathSize - 1) && (secondPath.endsWith("/"))) {
                    // Hack for making wildcards work with trailing slash
                    secondPathParts.add("");
                    secondPathParts.add("");
                    pathSize += 2;
                }

                if (thisPathSize < pathSize) {
                    for (int i = 0; i < thisPathSize; i++) {
                        String thisPathPart = firstPathParts.get(i);
                        String pathPart = secondPathParts.get(i);
                        if (thisPathPart.equals("*") && (i == thisPathSize - 1) && firstPath.endsWith("*")) {
                            // wildcard match
                            return true;
                        }
                        if (!thisPathPart.startsWith(":")
                                && !thisPathPart.equals(pathPart)
                                && !thisPathPart.equals("*")) {
                            return false;
                        }
                    }
                    // All parts matched
                    return true;
                }
                // End check wild card
            }
            return false;
        }
    }
}
