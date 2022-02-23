package org.broadinstitute.dsm.model.elastic.filter.query;

import org.elasticsearch.index.query.QueryBuilder;

public class SingleQueryBuilder extends BaseQueryBuilder {

    @Override
    protected QueryBuilder build(QueryBuilder queryBuilder) {
        return queryBuilder;
    }
}
