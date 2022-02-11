package org.broadinstitute.dsm.model.elastic.filter.query;

import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.export.parse.Parser;
import org.broadinstitute.dsm.model.elastic.filter.AndOrFilterSeparator;
import org.broadinstitute.dsm.model.elastic.filter.FilterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.broadinstitute.dsm.model.elastic.filter.splitter.BaseSplitter;
import org.broadinstitute.dsm.model.elastic.filter.splitter.SplitterFactory;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

import java.util.List;
import java.util.Map;

public class DsmAbstractQueryBuilder {

    protected static final String DSM_WITH_DOT = ESObjectConstants.DSM + DBConstants.ALIAS_DELIMITER;
    protected String filter;
    protected Parser parser;
    protected BoolQueryBuilder boolQueryBuilder;
    protected QueryBuilder queryBuilder;
    protected BaseSplitter splitter;
    protected AndOrFilterSeparator filterSeparator;
    private BaseQueryBuilder baseQueryBuilder;

    public DsmAbstractQueryBuilder() {
        boolQueryBuilder = new BoolQueryBuilder();
    }

    public void setFilter(String filter) {
        this.filter = filter;
        this.filterSeparator = new AndOrFilterSeparator(filter);
    }

    public void setParser(Parser parser) {
        this.parser = parser;
    }

    public AbstractQueryBuilder build() {
        Map<String, List<String>> parsedFilters = filterSeparator.parseFiltersByLogicalOperators();
        for (Map.Entry<String, List<String>> parsedFilter: parsedFilters.entrySet()) {
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
            splitter = SplitterFactory.createSplitter(operator, filterValue);
            splitter.setFilter(filterValue);
            baseQueryBuilder = BaseQueryBuilder.of(splitter.getAlias(), splitter.getFieldName());
            QueryPayload queryPayload = new QueryPayload(buildPath(), splitter.getInnerProperty(), parser.parse(splitter.getValue()));
            filterStrategy.build(boolQueryBuilder, baseQueryBuilder.buildEachQuery(operator, queryPayload, splitter));
        }
    }

    protected String buildPath() {
        return DSM_WITH_DOT + Util.TABLE_ALIAS_MAPPINGS.get(splitter.getAlias()).getPropertyName();
    }


}
