package org.broadinstitute.dsm.model.elastic.filter.query;

import org.elasticsearch.index.query.QueryBuilder;

public class SingleQueryBuilder extends BaseQueryBuilder {

    public SingleQueryBuilder(QueryPayload queryPayload) {
        super(queryPayload);
    }

    public SingleQueryBuilder() {
        super(null);
    }

    @Override
    protected QueryBuilder getFinalQuery(QueryBuilder query) {
        return query;
    }
}
