package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.export.generate.PropertyInfo;
import org.broadinstitute.dsm.model.elastic.export.parse.Parser;
import org.broadinstitute.dsm.model.elastic.filter.AndOrFilterSeparator;
import org.broadinstitute.dsm.model.elastic.filter.FilterParser;
import org.broadinstitute.dsm.model.elastic.filter.FilterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.broadinstitute.dsm.model.elastic.filter.splitter.SplitterStrategy;
import org.broadinstitute.dsm.model.participant.Util;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

public class BaseAbstractQueryBuilder {

    protected static final String DSM_WITH_DOT = ESObjectConstants.DSM + DBConstants.ALIAS_DELIMITER;

    protected String filter;
    protected Parser parser;
    protected String esIndex;
    protected Integer ddpInstanceId;
    protected BoolQueryBuilder boolQueryBuilder;
    protected QueryBuilder queryBuilder;
    protected SplitterStrategy splitter;
    protected AndOrFilterSeparator filterSeparator;
    private BaseQueryBuilder baseQueryBuilder;

    protected BaseAbstractQueryBuilder() {
        boolQueryBuilder = new BoolQueryBuilder();
        parser = new FilterParser();
    }

    public void setEsIndex(String index) {
        this.esIndex = index;
    }

    public void setDdpInstanceId(Integer ddpInstanceId) {
        this.ddpInstanceId = ddpInstanceId;
    }

    public void setFilter(String filter) {
        this.filter = filter;
        boolQueryBuilder = new BoolQueryBuilder();
    }

    public void setFilterSeparator(AndOrFilterSeparator filterSeparator) {
        this.filterSeparator = filterSeparator;
    }

    public void setParser(Parser parser) {
        this.parser = parser;
    }

    public AbstractQueryBuilder<?> build() {
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
        Map<String, List<QueryBuilder>> nestedQueryBuilderLists = new HashMap<>();
        Map<String, BaseQueryBuilder> correctBaseQueryBuilder = new HashMap<>();
        //all alias are mixed in here. order them per alias?
        for (String filterValue : filterValues) {
            creatingQueryBuilder(filterValue, filterStrategy, nestedQueryBuilderLists, correctBaseQueryBuilder);
        }
        //including check for ddpInstanceId only needed for OS studies
        if (ddpInstanceId != null && esIndex != null && DBConstants.OSTEO_INDEX.equals(esIndex)) {
            if (nestedQueryBuilderLists.containsKey(ESObjectConstants.DSM + DBConstants.ALIAS_DELIMITER + ESObjectConstants.KIT_REQUEST_SHIPPING)) {
                creatingQueryBuilder(DBConstants.DDP_KIT_REQUEST_ALIAS + DBConstants.ALIAS_DELIMITER
                                + ESObjectConstants.DDP_INSTANCE_ID_CAMEL_CASE + Filter.EQUALS + ddpInstanceId,
                        filterStrategy, nestedQueryBuilderLists, correctBaseQueryBuilder);
            }
            if (nestedQueryBuilderLists.containsKey(ESObjectConstants.DSM + DBConstants.ALIAS_DELIMITER + ESObjectConstants.MEDICAL_RECORD)) {
                creatingQueryBuilder(DBConstants.DDP_MEDICAL_RECORD_ALIAS + DBConstants.ALIAS_DELIMITER
                                + ESObjectConstants.DDP_INSTANCE_ID_CAMEL_CASE + Filter.EQUALS + ddpInstanceId,
                        filterStrategy, nestedQueryBuilderLists, correctBaseQueryBuilder);
            }
            if (nestedQueryBuilderLists.containsKey(ESObjectConstants.DSM + DBConstants.ALIAS_DELIMITER + ESObjectConstants.ONC_HISTORY_DETAIL)) {
                creatingQueryBuilder(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER
                                + ESObjectConstants.DDP_INSTANCE_ID_CAMEL_CASE + Filter.EQUALS + ddpInstanceId,
                        filterStrategy, nestedQueryBuilderLists, correctBaseQueryBuilder);
            }
            //tissue and oncHistory objects do currently not have ddpInstanceId set
        }
        if (!nestedQueryBuilderLists.isEmpty() && nestedQueryBuilderLists.size() == correctBaseQueryBuilder.size()) {
            nestedQueryBuilderLists.entrySet().stream().forEach(entrySet -> filterStrategy.build(boolQueryBuilder,
                    correctBaseQueryBuilder.get(entrySet.getKey()).build(entrySet.getValue())));
        }
    }

    private void creatingQueryBuilder(String filterValue, FilterStrategy filterStrategy,
                                      Map<String, List<QueryBuilder>> nestedQueryBuilderLists,
                                      Map<String, BaseQueryBuilder> correctBaseQueryBuilder) {
        Operator operator = Operator.extract(filterValue);
        if (operator == null) {
            return;
        }
        splitter = operator.getSplitterStrategy();
        splitter.setFilterSeparator(filterSeparator);
        splitter.setFilter(filterValue);
        String path = PropertyInfo.of(splitter.getAlias()).getPropertyName();
        String alias = Objects.requireNonNull(splitter.getAlias());
        if (Util.isUnderDsmKey(alias)) {
            path = DSM_WITH_DOT + PropertyInfo.of(splitter.getAlias()).getPropertyName();
        }
        String innerProperty = splitter.getInnerProperty();
        if (alias.equals(ESObjectConstants.PARTICIPANT_DATA)) {
            innerProperty = "dynamicFields." + innerProperty;
        }
        QueryPayload queryPayload = new QueryPayload(path, innerProperty, splitter.getAlias(),
                parser.parse(splitter.getValue()), esIndex);
        baseQueryBuilder = BaseQueryBuilder.of(queryPayload);
        List<QueryBuilder> queryBuilders =
                QueryStrategyFactory.create(new QueryStrategyFactoryPayload(baseQueryBuilder, operator, parser)).build();

        if (StringUtils.isNotBlank(path)) {
            if (!nestedQueryBuilderLists.containsKey(path)) {
                nestedQueryBuilderLists.put(path, queryBuilders);
                correctBaseQueryBuilder.put(path, baseQueryBuilder);
            } else {
                List<QueryBuilder> queryBuildersFromMap = nestedQueryBuilderLists.get(path);
                queryBuildersFromMap.addAll(queryBuilders);
            }
        } else {
            filterStrategy.build(boolQueryBuilder, baseQueryBuilder.build(queryBuilders));
        }
    }
}
