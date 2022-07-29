package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.Arrays;
import java.util.List;

import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.broadinstitute.dsm.model.elastic.filter.splitter.SplitterStrategy;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

public class BaseActivityStrategy {

    private static final List<String> BASE_ACTIVITY_PROPERTIES = Arrays.asList(
            ElasticSearchUtil.CREATED_AT, ElasticSearchUtil.COMPLETED_AT, ElasticSearchUtil.LAST_UPDATED, ElasticSearchUtil.STATUS
    );
    private SplitterStrategy splitter;
    private Operator operator;

    protected BaseActivityStrategy(SplitterStrategy splitter, Operator operator) {
        this.splitter = splitter;
        this.operator = operator;
    }

    public static BaseActivityStrategy of(SplitterStrategy splitter, Operator operator) {
        splitter.setCamelCaseConverter(CamelCaseConverter.of());
        BaseActivityStrategy strategy = new BaseActivityStrategy(splitter, operator);
        if (!BASE_ACTIVITY_PROPERTIES.contains(splitter.getFieldName())) {
            strategy = new QuestionsAnswersActivityStrategy(splitter, operator);
        }
        return strategy;
    }

    public void apply() {
        SingleQueryBuilder singleQueryBuilder = new SingleQueryBuilder();
        singleQueryBuilder.setOperator(operator);
        QueryPayload queryPayload = new QueryPayload("activities", "activityCode", new String[]{splitter.getAlias()});
        singleQueryBuilder.setPayload(queryPayload);
        MatchQueryStrategy additionalQueryStrategy = new MatchQueryStrategy(singleQueryBuilder);
        operator.getQueryStrategy(additionalQueryStrategy);
    }

}
