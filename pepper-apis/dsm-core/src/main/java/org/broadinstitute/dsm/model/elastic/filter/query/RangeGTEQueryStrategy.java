package org.broadinstitute.dsm.model.elastic.filter.query;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;

public class RangeGTEQueryStrategy implements BuildQueryStrategy{
    @Override
    public QueryBuilder build(BaseQueryBuilder baseQueryBuilder) {
        RangeQueryBuilder greaterRangeQuery = new RangeQueryBuilder(baseQueryBuilder.payload.getFieldName());
        greaterRangeQuery.gte(baseQueryBuilder.payload.getValues()[0]);
        return baseQueryBuilder.build(greaterRangeQuery);
    }
}
