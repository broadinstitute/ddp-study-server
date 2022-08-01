package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.broadinstitute.dsm.model.elastic.converters.camelcase.NullObjectCamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.export.parse.Parser;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.broadinstitute.dsm.model.elastic.filter.splitter.SplitterStrategy;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.index.query.QueryBuilder;

public abstract class BaseActivitiesStrategy {
    private static final List<String> BASE_ACTIVITY_PROPERTIES = Arrays.asList(
            ElasticSearchUtil.CREATED_AT, ElasticSearchUtil.COMPLETED_AT, ElasticSearchUtil.LAST_UPDATED, ElasticSearchUtil.STATUS
    );
    protected SplitterStrategy splitter;
    protected Operator operator;
    protected BaseQueryBuilder baseQueryBuilder;
    protected Parser parser;

    public BaseActivitiesStrategy(SplitterStrategy splitter, Operator operator, BaseQueryBuilder baseQueryBuilder, Parser parser) {
        this.splitter = splitter;
        this.operator = operator;
        this.baseQueryBuilder = baseQueryBuilder;
        this.parser = parser;
    }

    public static BaseActivitiesStrategy of(Parser parser, SplitterStrategy splitter,
                                            Operator operator, BaseQueryBuilder baseQueryBuilder) {
        splitter.setCamelCaseConverter(NullObjectCamelCaseConverter.of());
        BaseActivitiesStrategy strategy = new ActivityStrategy(parser, splitter, operator, baseQueryBuilder);
        if (!BASE_ACTIVITY_PROPERTIES.contains(splitter.getFieldName())) {
            strategy = new QuestionsAnswersActivityStrategy(parser, splitter, operator, baseQueryBuilder);
        }
        return strategy;
    }

    public List<QueryBuilder> apply() {
        QueryPayload queryPayload = new QueryPayload("activities", "activityCode", new String[]{splitter.getAlias()});
        SingleQueryBuilder singleQueryBuilder = new SingleQueryBuilder(queryPayload);
        singleQueryBuilder.setPayload(queryPayload);
        MatchQueryStrategy additionalQueryStrategy = new MatchQueryStrategy(singleQueryBuilder);
        additionalQueryStrategy.setBaseQueryBuilder(singleQueryBuilder);
        List<QueryBuilder> result = new ArrayList<>();
        result.addAll(additionalQueryStrategy.build());
        result.addAll(getSpecificQueries());
        return result;
    }

    protected abstract List<QueryBuilder> getSpecificQueries();
}
