package org.broadinstitute.dsm.model.elastic.filter.query;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

public class MustExistsQueryStrategy implements BuildQueryStrategy {
    @Override
    public QueryBuilder build(BaseQueryBuilder baseQueryBuilder) {
        BoolQueryBuilder isNotNullAndNotEmpty = new BoolQueryBuilder();
        isNotNullAndNotEmpty.must(baseQueryBuilder.build(new ExistsQueryBuilder(baseQueryBuilder.payload.getFieldName())));
        return isNotNullAndNotEmpty;
    }
}
