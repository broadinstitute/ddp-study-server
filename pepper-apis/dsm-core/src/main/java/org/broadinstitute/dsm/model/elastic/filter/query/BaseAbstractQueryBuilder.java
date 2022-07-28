package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.export.generate.PropertyInfo;
import org.broadinstitute.dsm.model.elastic.export.parse.Parser;
import org.broadinstitute.dsm.model.elastic.filter.AndOrFilterSeparator;
import org.broadinstitute.dsm.model.elastic.filter.FilterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.broadinstitute.dsm.model.elastic.filter.splitter.SplitterStrategy;
import org.broadinstitute.dsm.model.participant.Util;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

public class BaseAbstractQueryBuilder {
    protected String filter;
    protected Parser parser;
    protected String esIndex;
    protected BoolQueryBuilder boolQueryBuilder;
    protected QueryBuilder queryBuilder;
    protected SplitterStrategy splitter;
    protected AndOrFilterSeparator filterSeparator;
    private BaseQueryBuilder baseQueryBuilder;

    protected BaseAbstractQueryBuilder() {
        boolQueryBuilder = new BoolQueryBuilder();
    }

    public void setEsIndex(String index) {
        this.esIndex = index;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    public void setFilterSeparator(AndOrFilterSeparator filterSeparator) {
        this.filterSeparator = filterSeparator;
    }

    public void setParser(Parser parser) {
        this.parser = parser;
    }

    public AbstractQueryBuilder build() {
        filterSeparator.setFilter(filter);
        Map<String, List<String>> parsedFilters = filterSeparator.parseFiltersByLogicalOperators();
        for (Map.Entry<String, List<String>> parsedFilter : parsedFilters.entrySet()) {
            List<String> filterValues = parsedFilter.getValue();
            if (parsedFilter.getKey().equals(Filter.AND_TRIMMED)) {
                buildUpQuery(filterValues, BoolQueryBuilder::must);
            } else {
                buildUpQuery(filterValues, BoolQueryBuilder::should);
            }
        }
        return boolQueryBuilder;
    }

    protected void buildUpQuery(List<String> filterValues, FilterStrategy filterStrategy) {
        for (String filterValue : filterValues) {
            Operator operator = Operator.extract(filterValue);
            splitter = operator.getSplitterStrategy();
            splitter.setFilter(filterValue);
            baseQueryBuilder = BaseQueryBuilder.of(splitter.getAlias(), splitter.getFieldName());
            if (!Util.isUnderDsmKey(splitter.getAlias())) {
                SingleQueryBuilder singleQueryBuilder = new SingleQueryBuilder();
                singleQueryBuilder.setOperator(operator);
                QueryPayload queryPayload = new QueryPayload("activities", "activityCode", new String[]{splitter.getAlias()});
                singleQueryBuilder.setPayload(queryPayload);
                MatchQueryStrategy additionalQueryStrategy = new MatchQueryStrategy(singleQueryBuilder);
                operator.getQueryStrategy(additionalQueryStrategy);
            }
            QueryPayload queryPayload = new QueryPayload(
                    buildPath(), splitter.getInnerProperty(), parser.parse(splitter.getValue()), esIndex
            );
            filterStrategy.build(boolQueryBuilder, baseQueryBuilder.buildEachQuery(operator, queryPayload));
        }
    }

    protected String buildPath() {
        return PropertyInfo.of(splitter.getAlias()).getPropertyName();
    }
}
