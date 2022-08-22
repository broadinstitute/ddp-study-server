package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.model.elastic.mapping.TypeExtractor;
import org.elasticsearch.index.query.QueryBuilder;

public interface BuildQueryStrategy {

    List<QueryBuilder> build(BaseQueryBuilder baseQueryBuilder);

    List<QueryBuilder> build();

    default void setExtractor(TypeExtractor<Map<String, String>> typeExtractor) {}

    default void addAdditionalQueryStrategy(BuildQueryStrategy... queryStrategy) {

    }

    BaseQueryBuilder getBaseQueryBuilder();

    QueryBuilder getMainQueryBuilder(BaseQueryBuilder baseQueryBuilder);

    void setBaseQueryBuilder(BaseQueryBuilder baseQueryBuilder);
}
