package org.broadinstitute.dsm.model.elastic.filter.query;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

public class MustNotExistsQueryStrategy extends BaseQueryStrategy {

    @Override
    protected QueryBuilder getMainQueryBuilderFromChild(BaseQueryBuilder baseQueryBuilder) {
        BoolQueryBuilder existsWithEmpty = new BoolQueryBuilder();
        existsWithEmpty.mustNot(new ExistsQueryBuilder(baseQueryBuilder.payload.getFieldName()));
        return existsWithEmpty;
    }
}
