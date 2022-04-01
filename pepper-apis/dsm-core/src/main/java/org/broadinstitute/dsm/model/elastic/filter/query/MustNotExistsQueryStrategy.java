package org.broadinstitute.dsm.model.elastic.filter.query;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

public class MustNotExistsQueryStrategy implements BuildQueryStrategy {
    @Override
    public QueryBuilder build(BaseQueryBuilder baseQueryBuilder) {
        BoolQueryBuilder existsWithEmpty = new BoolQueryBuilder();
        existsWithEmpty.mustNot(baseQueryBuilder.build(new ExistsQueryBuilder(baseQueryBuilder.payload.getFieldName())));
        return existsWithEmpty;
    }
}
