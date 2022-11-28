package org.broadinstitute.dsm.model.dashboard;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.Filter;
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
                buildQueryBuilderFromQueryString(boolQueryBuilder, filterStrategy, filterValue);
            }
        }
        addDatePeriod(boolQueryBuilder, queryBuildPayload.getLabel().getDashboardFilterDto().getDatePeriodField(),
                Filter.LARGER_EQUALS_TRIMMED, queryBuildPayload.getStartDate());
        addDatePeriod(boolQueryBuilder, queryBuildPayload.getLabel().getDashboardFilterDto().getDatePeriodField(),
                Filter.SMALLER_EQUALS_TRIMMED, queryBuildPayload.getEndDate());
        return boolQueryBuilder;
    }

    private void addDatePeriod(BoolQueryBuilder boolQueryBuilder, String field, String queryOperator, String date) {
        if (queryBuildPayload.getStartDate() != null
                && StringUtils.isNotBlank(queryBuildPayload.getLabel().getDashboardFilterDto().getDatePeriodField())) {
            FilterStrategy filterStrategy = FilterStrategy.of(Filter.AND_TRIMMED);
            String filter = String.format("%s %s '%s' ", field, queryOperator, date);
            //TODO test is changing it to right query. here not
            // still prequal instead of nested!
            buildQueryBuilderFromQueryString(boolQueryBuilder, filterStrategy, filter);
        }
    }

    private void buildQueryBuilderFromQueryString(BoolQueryBuilder boolQueryBuilder, FilterStrategy filterStrategy, String filter) {
        Operator operator = Operator.extract(filter);
        SplitterStrategy splitterStrategy = operator.getSplitterStrategy();
        splitterStrategy.setFilter(filter);
        QueryPayload queryPayload = buildQueryPayload(splitterStrategy);
        BuildQueryStrategy queryStrategy = getQueryStrategy(operator, queryPayload);
        filterStrategy.build(boolQueryBuilder,
                getBaseQueryBuilder(queryPayload).build(buildQueries(queryStrategy)));
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
        return new QueryPayload(queryBuildPayload.getLabel().getDashboardFilterDto().getEsNestedPath(),
                queryBuildPayload.getLabel().getDashboardFilterDto().getEsFilterPath(), valueParser.parse(splitterStrategy.getValue()),
                queryBuildPayload.getEsParticipantsIndex());
    }

    protected Map<String, List<String>> getSeparatedFilters() {
        queryBuildPayload.getSeparator().setFilter(queryBuildPayload.getLabel().getDashboardFilterDto().getAdditionalFilter());
        return queryBuildPayload.getSeparator().parseFiltersByLogicalOperators();
    }

}
