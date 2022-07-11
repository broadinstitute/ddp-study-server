package org.broadinstitute.dsm.model.dashboard;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.filter.query.BuildQueryStrategy;

class QueryBuilderStrategyFactory {

    private QueryBuildPayload queryBuildPayload;

    public QueryBuilderStrategyFactory(QueryBuildPayload queryBuildPayload) {
        this.queryBuildPayload = queryBuildPayload;
    }

    public BaseQueryBuilderStrategy of() {
        if (StringUtils.isNotBlank(queryBuildPayload.getEsNestedPath())) {
            return new NestedQueryBuilderStrategy(queryBuildPayload);
        }
        return new SingleQueryBuilderStrategy(queryBuildPayload);
    }

}
