package org.broadinstitute.dsm.model.filter;

import spark.QueryParamsMap;

public interface Filterable<T> {

    T filter(QueryParamsMap queryParamsMap);

    void setFrom(int from);

    void setTo(int to);

    void setParseDtos(boolean parseDtos);
}
