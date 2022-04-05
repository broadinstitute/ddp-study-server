package org.broadinstitute.dsm.model.elastic.filter.query;

import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

public class MatchQueryStrategy implements BuildQueryStrategy{

    @Override
    public QueryBuilder build(BaseQueryBuilder baseQueryBuilder) {
        return baseQueryBuilder
                .build(new MatchQueryBuilder(baseQueryBuilder.payload.getFieldName(), baseQueryBuilder.payload.getValues()[0]));
    }
}
