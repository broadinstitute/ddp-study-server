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
import org.broadinstitute.dsm.model.elastic.filter.query.CollectionQueryBuilder;
import org.broadinstitute.dsm.model.elastic.filter.query.QueryPayload;
import org.broadinstitute.dsm.model.elastic.filter.query.SingleQueryBuilder;
import org.broadinstitute.dsm.model.elastic.filter.splitter.SplitterStrategy;
import org.broadinstitute.dsm.model.elastic.sort.Alias;
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
                buildQueryBuilderFromQueryString(boolQueryBuilder, filterStrategy, filterValue, null);
            }
        }
        if (queryBuildPayload.getStartDate() != null && StringUtils.isNotBlank(
                queryBuildPayload.getLabel().getDashboardFilterDto().getDatePeriodField())) {
            addDatePeriod(boolQueryBuilder, queryBuildPayload.getLabel().getDashboardFilterDto().getDatePeriodField(),
                    Filter.LARGER_EQUALS_TRIMMED, queryBuildPayload.getStartDate());
            addDatePeriod(boolQueryBuilder, queryBuildPayload.getLabel().getDashboardFilterDto().getDatePeriodField(),
                    Filter.SMALLER_EQUALS_TRIMMED, queryBuildPayload.getEndDate());
        }
        return boolQueryBuilder;
    }

    private void addDatePeriod(BoolQueryBuilder boolQueryBuilder, String datePeriodField, String queryOperator, String date) {
        FilterStrategy filterStrategy = FilterStrategy.of(Filter.AND_TRIMMED);
        String filter = String.format("%s %s '%s' ", datePeriodField, queryOperator, date);
        buildQueryBuilderFromQueryString(boolQueryBuilder, filterStrategy, filter, datePeriodField);
    }

    private void buildQueryBuilderFromQueryString(BoolQueryBuilder boolQueryBuilder, FilterStrategy filterStrategy, String filter,
                                                  String datePeriodField) {
        Operator operator = Operator.extract(filter);
        SplitterStrategy splitterStrategy = operator.getSplitterStrategy();
        splitterStrategy.setFilter(filter);
        QueryPayload queryPayload = buildQueryPayload(splitterStrategy, datePeriodField);
        BuildQueryStrategy queryStrategy = getQueryStrategy(operator, queryPayload);
        BaseQueryBuilder baseQueryBuilder = getBaseQueryBuilder(queryPayload);
        if (datePeriodField != null) {
            baseQueryBuilder = new SingleQueryBuilder();
            if (Alias.of(queryPayload.getAlias()).isCollection()) {
                baseQueryBuilder = new CollectionQueryBuilder(queryPayload);
            }
        }
        List queryBuilders = buildQueries(queryStrategy);
        QueryBuilder newQuery = baseQueryBuilder.build(queryBuilders);
        filterStrategy.build(boolQueryBuilder, newQuery);
    }

    protected QueryPayload buildQueryPayload(SplitterStrategy splitterStrategy, String datePeriodField) {
        if (datePeriodField != null) {
            return new QueryPayload(splitterStrategy.getAlias(), splitterStrategy.getInnerProperty(), splitterStrategy.getAlias(),
                    valueParser.parse(splitterStrategy.getValue()), queryBuildPayload.getEsParticipantsIndex());
        }
        return new QueryPayload(queryBuildPayload.getLabel().getDashboardFilterDto().getEsNestedPath(),
                queryBuildPayload.getLabel().getDashboardFilterDto().getEsFilterPath(),
                valueParser.parse(splitterStrategy.getValue()),
                queryBuildPayload.getEsParticipantsIndex());
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

    protected Map<String, List<String>> getSeparatedFilters() {
        queryBuildPayload.getSeparator().setFilter(queryBuildPayload.getLabel().getDashboardFilterDto().getAdditionalFilter());
        return queryBuildPayload.getSeparator().parseFiltersByLogicalOperators();
    }

}
