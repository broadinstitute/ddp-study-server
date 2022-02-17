package org.broadinstitute.dsm.model.elastic.filter;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

public interface FilterStrategy {

    void build(BoolQueryBuilder boolQueryBuilder, QueryBuilder queryBuilder);

}
