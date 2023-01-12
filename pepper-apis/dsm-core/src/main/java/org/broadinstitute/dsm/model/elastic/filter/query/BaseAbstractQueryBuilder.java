package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.export.generate.PropertyInfo;
import org.broadinstitute.dsm.model.elastic.export.parse.Parser;
import org.broadinstitute.dsm.model.elastic.filter.AndOrFilterSeparator;
import org.broadinstitute.dsm.model.elastic.filter.FilterParser;
import org.broadinstitute.dsm.model.elastic.filter.FilterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.broadinstitute.dsm.model.elastic.filter.splitter.SplitterStrategy;
import org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter;
import org.broadinstitute.dsm.model.participant.Util;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

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
        //just osteo2
        if (ddpInstanceId != null) {
            if (nestedQueryBuilderLists.containsKey("dsm.kitRequestShipping")) {
                creatingQueryBuilder("k.ddpInstanceId = " + ddpInstanceId,
                        filterStrategy, nestedQueryBuilderLists, correctBaseQueryBuilder);
            }
            if (nestedQueryBuilderLists.containsKey("dsm.medicalRecord")) {
                creatingQueryBuilder("m.ddpInstanceId = " + ddpInstanceId,
                        filterStrategy, nestedQueryBuilderLists, correctBaseQueryBuilder);
            }
            if (nestedQueryBuilderLists.containsKey("dsm.oncHistoryDetail")) {
                creatingQueryBuilder("oD.ddpInstanceId = " + ddpInstanceId,
                        filterStrategy, nestedQueryBuilderLists, correctBaseQueryBuilder);
            }
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

    private AbstractQueryBuilder addOsteo2InstanceFilter(AbstractQueryBuilder queryBuilder) {
        //just osteo2
        BoolQueryBuilder osteo2QueryBuilder = new BoolQueryBuilder();
        osteo2QueryBuilder.should(osteoVersion2Surveys("CONSENT"));
        osteo2QueryBuilder.should(osteoVersion2Surveys("CONSENT_ASSENT"));
        osteo2QueryBuilder.should(osteoVersion2Surveys("PARENTAL_CONSENT"));
        osteo2QueryBuilder.should(osteoVersion2Surveys("LOVEDONE"));
        ((BoolQueryBuilder) queryBuilder).must(osteo2QueryBuilder);
        return queryBuilder;
    }

    private NestedQueryBuilder osteoVersion2Surveys(String stableId) {
        BoolQueryBuilder queryBuilderConsentV2 = new BoolQueryBuilder();
        queryBuilderConsentV2.must(new MatchQueryBuilder("activities.activityCode", stableId).operator(
                org.elasticsearch.index.query.Operator.AND));
        queryBuilderConsentV2.must(
                QueryBuilders.matchQuery("activities.activityVersion", "v2").operator(org.elasticsearch.index.query.Operator.AND));
        queryBuilderConsentV2.must(new BoolQueryBuilder().must(new ExistsQueryBuilder("activities.completedAt")));
        NestedQueryBuilder expectedNestedQueryConsent = new NestedQueryBuilder("activities", queryBuilderConsentV2, ScoreMode.Avg);
        return expectedNestedQueryConsent;
    }

}
