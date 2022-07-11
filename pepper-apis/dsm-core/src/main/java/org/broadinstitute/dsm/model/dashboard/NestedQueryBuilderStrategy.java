package org.broadinstitute.dsm.model.dashboard;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.filter.FilterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.broadinstitute.dsm.model.elastic.filter.query.QueryPayload;
import org.broadinstitute.dsm.model.elastic.filter.splitter.SplitterStrategy;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

public class NestedQueryBuilderStrategy extends BaseQueryBuilderStrategy {

    public NestedQueryBuilderStrategy(QueryBuildPayload queryBuildPayload) {
        super(queryBuildPayload);

    }

    @Override
    public QueryBuilder build() {
        QueryBuilder queryBuilder;
        if (hasAdditionalFilter()) {
            queryBuilder = buildQueryForAdditionalFilter();
        } else {
            queryBuilder = buildQueryForNoAdditionalFilter();
        }
        return queryBuilder;
    }

    private QueryBuilder buildQueryForNoAdditionalFilter() {
        QueryBuilder queryBuilder;
        QueryPayload queryPayload = new QueryPayload(queryBuildPayload.getLabel().getDashboardFilterDto().getEsNestedPath(),
                queryBuildPayload.getLabel().getDashboardFilterDto().getEsFilterPath(),
                new Object[] {queryBuildPayload.getLabel().getDashboardFilterDto().getEsFilterPathValue()},
                queryBuildPayload.getEsParticipantsIndex());
        queryBuilder = queryBuildPayload.getBaseQueryBuilder().buildEachQuery(Operator.EQUALS, queryPayload);
        return queryBuilder;
    }

    private QueryBuilder buildQueryForAdditionalFilter() {
        QueryBuilder queryBuilder;
        queryBuildPayload.getSeparator().setFilter(queryBuildPayload.getLabel().getDashboardFilterDto().getAdditionalFilter());
        Map<String, List<String>> andOrSeparated = queryBuildPayload.getSeparator().parseFiltersByLogicalOperators();
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        for (Map.Entry<String, List<String>> parsedFilter : andOrSeparated.entrySet()) {
            FilterStrategy filterStrategy = FilterStrategy.of(parsedFilter.getKey());
            for (String filterValue: parsedFilter.getValue()) {
                Operator operator = Operator.extract(filterValue);
                SplitterStrategy splitterStrategy = operator.getSplitterStrategy();
                splitterStrategy.setFilter(filterValue);
                String[] values = splitterStrategy.getValue();
                QueryPayload queryPayload = new QueryPayload(queryBuildPayload.getLabel().getDashboardFilterDto().getEsNestedPath(),
                        queryBuildPayload.getLabel().getDashboardFilterDto().getEsFilterPath(),
                        values, queryBuildPayload.getEsParticipantsIndex());
                filterStrategy.build(boolQueryBuilder, queryBuildPayload.getBaseQueryBuilder().buildEachQuery(operator, queryPayload));
            }
        }
        queryBuilder = boolQueryBuilder;
        return queryBuilder;
    }

    private boolean hasAdditionalFilter() {
        return StringUtils.isNotBlank(queryBuildPayload.getLabel().getDashboardFilterDto().getAdditionalFilter());
    }
}
