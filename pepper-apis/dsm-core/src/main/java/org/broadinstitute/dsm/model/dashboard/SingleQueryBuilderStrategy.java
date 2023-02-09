package org.broadinstitute.dsm.model.dashboard;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.filter.query.QueryPayload;

class SingleQueryBuilderStrategy extends BaseQueryBuilderStrategy {

    public SingleQueryBuilderStrategy(QueryBuildPayload queryBuildPayload) {
        super(queryBuildPayload);
    }

    @Override
    protected QueryPayload getQueryPayload() {
        return new QueryPayload(StringUtils.EMPTY,
                queryBuildPayload.getLabel().getDashboardFilterDto().getEsFilterPath(),
                List.of(queryBuildPayload.getLabel().getDashboardFilterDto().getEsFilterPathValue()).toArray(),
                queryBuildPayload.getEsParticipantsIndex());
    }
}
