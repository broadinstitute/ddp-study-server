package org.broadinstitute.dsm.model.elastic.filter.query;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;

public class RangeLTEQueryStrategy extends BaseQueryStrategy {

    @Override
    protected QueryBuilder getMainQueryBuilderFromChild(BaseQueryBuilder baseQueryBuilder) {
        RangeQueryBuilder lessRangeQuery = new RangeQueryBuilder(baseQueryBuilder.payload.getFieldName());
        lessRangeQuery.lte(baseQueryBuilder.payload.getValues()[0]);
        return lessRangeQuery;
    }
}
