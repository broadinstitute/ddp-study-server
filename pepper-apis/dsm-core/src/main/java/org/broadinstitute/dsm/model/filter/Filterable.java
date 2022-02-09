package org.broadinstitute.dsm.model.filter;

import spark.QueryParamsMap;

public interface Filterable<T> {

    T filter(QueryParamsMap queryParamsMap);


}
