package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.broadinstitute.dsm.model.elastic.converters.camelcase.NullObjectCamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.export.parse.Parser;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.index.query.QueryBuilder;

//abstract class to handle different way of building ES queries for objects which go under activities in ES
public abstract class BaseActivitiesStrategy extends BaseQueryStrategy {
    private static final List<String> BASE_ACTIVITY_PROPERTIES = Arrays.asList(
            ElasticSearchUtil.CREATED_AT, ElasticSearchUtil.COMPLETED_AT, ElasticSearchUtil.LAST_UPDATED, ElasticSearchUtil.STATUS
    );
    protected Operator operator;
    protected BaseQueryBuilder baseQueryBuilder;
    protected Parser parser;

    public BaseActivitiesStrategy(Operator operator, BaseQueryBuilder baseQueryBuilder, Parser parser) {
        this.operator = operator;
        this.baseQueryBuilder = baseQueryBuilder;
        this.parser = parser;
    }

    public static BaseActivitiesStrategy of(Parser parser,
                                            Operator operator, BaseQueryBuilder baseQueryBuilder) {
        operator.getSplitterStrategy().setCamelCaseConverter(NullObjectCamelCaseConverter.of());
        BaseActivitiesStrategy strategy = new ActivityStrategy(parser, operator, baseQueryBuilder);
        if (isQuestionsAnswersField(operator)) {
            strategy = new QuestionsAnswersActivityStrategy(parser, operator, baseQueryBuilder);
        }
        return strategy;
    }

    public static boolean isQuestionsAnswersField(Operator operator) {
        return !BASE_ACTIVITY_PROPERTIES.contains(operator.getSplitterStrategy().getFieldName());
    }

    @Override
    protected QueryBuilder getMainQueryBuilderFromChild(BaseQueryBuilder baseQueryBuilder) {
        throw new UnsupportedOperationException();
    }


    @Override
    public List<QueryBuilder> build() {
        MatchQueryStrategy additionalQueryStrategy = buildDefaultActivityCodeMatcher();
        List<QueryBuilder> result = new ArrayList<>();
        result.addAll(additionalQueryStrategy.build());
        result.addAll(getSpecificQueries());
        return result;
    }

    private MatchQueryStrategy buildDefaultActivityCodeMatcher() {
        QueryPayload queryPayload = new QueryPayload(
                ESObjectConstants.ACTIVITIES, ESObjectConstants.ACTIVITY_CODE, new String[]{operator.getSplitterStrategy().getAlias()});
        SingleQueryBuilder singleQueryBuilder = new SingleQueryBuilder(queryPayload);
        singleQueryBuilder.setPayload(queryPayload);
        MatchQueryStrategy additionalQueryStrategy = new MatchQueryStrategy(singleQueryBuilder);
        additionalQueryStrategy.setBaseQueryBuilder(singleQueryBuilder);
        return additionalQueryStrategy;
    }

    protected abstract List<QueryBuilder> getSpecificQueries();
}
