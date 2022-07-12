package org.broadinstitute.dsm.model.dashboard;

import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.broadinstitute.dsm.model.elastic.filter.query.QueryPayload;
import org.elasticsearch.index.query.QueryBuilder;

class NestedQueryBuilderStrategy extends BaseQueryBuilderStrategy {

    public NestedQueryBuilderStrategy(QueryBuildPayload queryBuildPayload) {
        super(queryBuildPayload);

    }

    protected QueryBuilder buildQueryForNoAdditionalFilter() {
        QueryPayload queryPayload = new QueryPayload(queryBuildPayload.getLabel().getDashboardFilterDto().getEsNestedPath(),
                queryBuildPayload.getLabel().getDashboardFilterDto().getEsFilterPath(),
                new Object[] {queryBuildPayload.getLabel().getDashboardFilterDto().getEsFilterPathValue()},
                queryBuildPayload.getEsParticipantsIndex());
        return queryBuildPayload.getBaseQueryBuilder().buildEachQuery(Operator.EQUALS, queryPayload);
    }

}
