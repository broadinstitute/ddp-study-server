package org.broadinstitute.dsm.model.filter;

import java.util.List;

import spark.QueryParamsMap;

public interface Filterable<T> {

    T filter(QueryParamsMap queryParamsMap);


}
