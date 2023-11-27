package org.broadinstitute.dsm.model.dashboard;

import org.broadinstitute.dsm.model.elastic.filter.query.QueryPayload;

class NestedQueryBuilderStrategy extends BaseQueryBuilderStrategy {

    public NestedQueryBuilderStrategy(QueryBuildPayload queryBuildPayload) {
        super(queryBuildPayload);
    }

    @Override
    protected QueryPayload getQueryPayload() {
        return new QueryPayload(queryBuildPayload.getLabel().getDashboardFilterDto().getEsNestedPath(),
                queryBuildPayload.getLabel().getDashboardFilterDto().getEsFilterPath(),
                new Object[] {queryBuildPayload.getLabel().getDashboardFilterDto().getEsFilterPathValue()},
                queryBuildPayload.getEsParticipantsIndex());
    }

}
