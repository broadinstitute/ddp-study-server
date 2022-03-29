package org.broadinstitute.dsm.model.elastic.filter.query;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

public class MultipleMatchQueryStrategy implements BuildQueryStrategy{
    @Override
    public QueryBuilder build(BaseQueryBuilder baseQueryBuilder) {
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        Object[] values = baseQueryBuilder.payload.getValues();
        for (Object value : values) {
            boolQueryBuilder.should(new MatchQueryBuilder(baseQueryBuilder.payload.getFieldName(), value));
        }
        baseQueryBuilder.build(boolQueryBuilder);
        return boolQueryBuilder;
    }
}
