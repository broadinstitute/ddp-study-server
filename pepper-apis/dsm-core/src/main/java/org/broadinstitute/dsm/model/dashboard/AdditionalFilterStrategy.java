package org.broadinstitute.dsm.model.dashboard;

import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.model.elastic.export.parse.DashboardValueParser;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;
import org.broadinstitute.dsm.model.elastic.filter.FilterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.broadinstitute.dsm.model.elastic.filter.query.BuildQueryStrategy;
import org.broadinstitute.dsm.model.elastic.filter.query.QueryPayload;
import org.broadinstitute.dsm.model.elastic.filter.splitter.SplitterStrategy;
import org.elasticsearch.index.query.BoolQueryBuilder;

//class to handle domain logic of additional filtering of dashboard
public class AdditionalFilterStrategy {
    protected QueryBuildPayload queryBuildPayload;
    private ValueParser valueParser;

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
                String[] values = splitterStrategy.getValue();
                QueryPayload queryPayload = new QueryPayload(
                        queryBuildPayload.getLabel().getDashboardFilterDto().getEsNestedPath(),
                        queryBuildPayload.getLabel().getDashboardFilterDto().getEsFilterPath(),
                        valueParser.parse(values),
                        queryBuildPayload.getEsParticipantsIndex());
                queryBuildPayload.getBaseQueryBuilder().setPayload(queryPayload);
                BuildQueryStrategy queryStrategy = operator.getQueryStrategy();
                queryStrategy.setBaseQueryBuilder(queryBuildPayload.getBaseQueryBuilder());
                queryBuildPayload.getBaseQueryBuilder().setOperator(operator);
                filterStrategy.build(boolQueryBuilder,
                        queryBuildPayload.getBaseQueryBuilder().build(queryStrategy.build()));
            }
        }
        return boolQueryBuilder;
    }

    protected Map<String, List<String>> getSeparatedFilters() {
        queryBuildPayload.getSeparator()
                .setFilter(queryBuildPayload.getLabel().getDashboardFilterDto().getAdditionalFilter());
        return queryBuildPayload.getSeparator().parseFiltersByLogicalOperators();
    }

}