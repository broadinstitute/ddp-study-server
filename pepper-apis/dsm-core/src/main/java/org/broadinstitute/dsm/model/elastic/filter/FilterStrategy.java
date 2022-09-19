package org.broadinstitute.dsm.model.elastic.filter;

import org.broadinstitute.dsm.model.Filter;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

public interface FilterStrategy {

    void build(BoolQueryBuilder boolQueryBuilder, QueryBuilder queryBuilder);

    static FilterStrategy of(String booleanOperator) {
        return Filter.AND_TRIMMED.equals(booleanOperator)
                ? BoolQueryBuilder::must
                : BoolQueryBuilder::should;
    }

}
