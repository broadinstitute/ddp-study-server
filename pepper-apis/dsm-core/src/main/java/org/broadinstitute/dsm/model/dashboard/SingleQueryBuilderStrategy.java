package org.broadinstitute.dsm.model.dashboard;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.broadinstitute.dsm.model.elastic.filter.query.QueryPayload;
import org.elasticsearch.index.query.QueryBuilder;

public class SingleQueryBuilderStrategy extends BaseQueryBuilderStrategy {
    public SingleQueryBuilderStrategy(QueryBuildPayload queryBuildPayload) {
        super(queryBuildPayload);
    }

    @Override
    public QueryBuilder build() {
        QueryBuilder queryBuilder;
        QueryPayload queryPayload = new QueryPayload(StringUtils.EMPTY, queryBuildPayload.getLabel().getDashboardFilterDto().getEsFilterPath(),
                List.of(queryBuildPayload.getLabel().getDashboardFilterDto().getEsFilterPathValue()).toArray(),
                queryBuildPayload.getEsParticipantsIndex());
        queryBuilder = queryBuildPayload.getBaseQueryBuilder().buildEachQuery(Operator.EQUALS, queryPayload);
        return queryBuilder;
    }
}
