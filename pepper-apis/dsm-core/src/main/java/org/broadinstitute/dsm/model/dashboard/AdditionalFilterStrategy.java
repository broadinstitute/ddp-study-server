package org.broadinstitute.dsm.model.dashboard;

import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.model.elastic.export.parse.DashboardValueParser;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;
import org.broadinstitute.dsm.model.elastic.filter.FilterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.broadinstitute.dsm.model.elastic.filter.query.BaseQueryBuilder;
import org.broadinstitute.dsm.model.elastic.filter.query.BuildQueryStrategy;
import org.broadinstitute.dsm.model.elastic.filter.query.QueryPayload;
import org.broadinstitute.dsm.model.elastic.filter.splitter.SplitterStrategy;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

//class to handle domain logic of additional filtering of dashboard
public class AdditionalFilterStrategy {
    protected QueryBuildPayload queryBuildPayload;
    protected ValueParser valueParser;

    public AdditionalFilterStrategy(QueryBuildPayload queryBuildPayload) {
        this.queryBuildPayload = queryBuildPayload;
        this.valueParser = new DashboardValueParser();
    }

    BoolQueryBuilder process() {
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        for (Map.Entry<String, List<String>> parsedFilter : getSeparatedFilters().entrySet()) {
            FilterStrategy filterStrategy = FilterStrategy.of(parsedFilter.getKey());
            for (String filterValue : parsedFilter.getValue()) {
                Operator operator = Operator.extract(filterValue);
                SplitterStrategy splitterStrategy = operator.getSplitterStrategy();
                splitterStrategy.setFilter(filterValue);
                QueryPayload queryPayload = buildQueryPayload(splitterStrategy);
                BuildQueryStrategy queryStrategy = getQueryStrategy(operator, queryPayload);
                filterStrategy.build(boolQueryBuilder,
                        getBaseQueryBuilder(queryPayload).build(buildQueries(queryStrategy)));
            }
        }
        return boolQueryBuilder;
    }

    protected List<QueryBuilder> buildQueries(BuildQueryStrategy queryStrategy) {
        return queryStrategy.build();
    }

    protected BuildQueryStrategy getQueryStrategy(Operator operator, QueryPayload queryPayload) {
        getBaseQueryBuilder(queryPayload).setPayload(queryPayload);
        BuildQueryStrategy queryStrategy = operator.getQueryStrategy();
        queryStrategy.setBaseQueryBuilder(getBaseQueryBuilder(queryPayload));
        getBaseQueryBuilder(queryPayload).setOperator(operator);
        queryStrategy.getBaseQueryBuilder().setOperator(operator);
        return queryStrategy;
    }

    protected BaseQueryBuilder getBaseQueryBuilder(QueryPayload queryPayload) {
        return queryBuildPayload.getBaseQueryBuilder();
    }

    protected QueryPayload buildQueryPayload(SplitterStrategy splitterStrategy) {
        return new QueryPayload(
                queryBuildPayload.getLabel().getDashboardFilterDto().getEsNestedPath(),
                queryBuildPayload.getLabel().getDashboardFilterDto().getEsFilterPath(),
                valueParser.parse(splitterStrategy.getValue()),
                queryBuildPayload.getEsParticipantsIndex());
    }

    protected Map<String, List<String>> getSeparatedFilters() {
        queryBuildPayload.getSeparator()
                .setFilter(queryBuildPayload.getLabel().getDashboardFilterDto().getAdditionalFilter());
        return queryBuildPayload.getSeparator().parseFiltersByLogicalOperators();
    }

}
