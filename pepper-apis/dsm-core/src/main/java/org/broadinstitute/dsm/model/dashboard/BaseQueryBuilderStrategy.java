package org.broadinstitute.dsm.model.dashboard;

import org.elasticsearch.index.query.QueryBuilder;

public abstract class BaseQueryBuilderStrategy {
    protected QueryBuildPayload queryBuildPayload;

    public BaseQueryBuilderStrategy(QueryBuildPayload queryBuildPayload) {
        this.queryBuildPayload = queryBuildPayload;
    }

    public abstract QueryBuilder build();
}
