package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.Map;

import org.broadinstitute.dsm.model.elastic.mapping.TypeExtractor;
import org.elasticsearch.index.query.QueryBuilder;

public interface BuildQueryStrategy {

    QueryBuilder build(BaseQueryBuilder baseQueryBuilder);

    default void setExtractor(TypeExtractor<Map<String, String>> typeExtractor) {}

}
