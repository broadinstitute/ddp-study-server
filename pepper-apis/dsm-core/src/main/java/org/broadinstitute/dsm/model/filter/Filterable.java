package org.broadinstitute.dsm.model.filter;

import spark.QueryParamsMap;
import org.broadinstitute.dsm.model.elastic.search.Deserializer;

public interface Filterable<T> {

    T filter(QueryParamsMap queryParamsMap);

    T filter(QueryParamsMap queryParamsMap, Deserializer deserializer);

    T filter(QueryParamsMap queryParamsMap, boolean noProxyDataNeeded);

    void setFrom(int from);

    void setTo(int to);
}
