package org.broadinstitute.dsm.model.elastic.filter.query;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

public class MustExistsQueryStrategy extends BaseQueryStrategy {

    @Override
    protected QueryBuilder getMainQueryBuilderFromChild(BaseQueryBuilder baseQueryBuilder) {
        BoolQueryBuilder isNotNullAndNotEmpty = new BoolQueryBuilder();
        isNotNullAndNotEmpty.must(new ExistsQueryBuilder(baseQueryBuilder.payload.getFieldName()));
        return isNotNullAndNotEmpty;
    }
}
