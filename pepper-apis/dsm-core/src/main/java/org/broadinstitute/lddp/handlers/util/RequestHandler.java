package org.broadinstitute.lddp.handlers.util;

import org.broadinstitute.lddp.util.CheckValidity;
import spark.QueryParamsMap;
import spark.Response;

import java.util.Map;

public interface RequestHandler<V extends CheckValidity> {
    Result process(V value, QueryParamsMap queryParamsMap, Map<String,String> pathParams, String requestMethod, String token, Response response) ;
}