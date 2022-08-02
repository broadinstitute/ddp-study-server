package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.List;

import org.broadinstitute.dsm.model.elastic.export.parse.Parser;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.elasticsearch.index.query.QueryBuilder;

public class QuestionsAnswersActivityStrategy extends BaseActivitiesStrategy {
    protected QuestionsAnswersActivityStrategy(Parser parser,
                                               Operator operator, BaseQueryBuilder baseQueryBuilder) {
        super(operator, baseQueryBuilder, parser);
    }

    @Override
    protected List<QueryBuilder> getSpecificQueries() {
        QueryPayload stableIdQueryPayload =
                new QueryPayload(
                        "activities.questionsAnswers", "stableId", new String[]{operator.getSplitterStrategy().getFieldName()});
        CollectionQueryBuilder stableIdCollectionQueryBuilder = new CollectionQueryBuilder(stableIdQueryPayload);
        stableIdCollectionQueryBuilder.setPayload(stableIdQueryPayload);
        MatchQueryStrategy stableIdQueryStrategy = new MatchQueryStrategy(stableIdCollectionQueryBuilder);
        stableIdCollectionQueryBuilder.setPayload(stableIdQueryPayload);
        stableIdQueryStrategy.setBaseQueryBuilder(stableIdCollectionQueryBuilder);

        QueryPayload answerQueryPayload =
                new QueryPayload(
                        "activities.questionsAnswers", "answer", parser.parse(operator.getSplitterStrategy().getValue()));
        CollectionQueryBuilder answerCollectionQueryBuilder = new CollectionQueryBuilder(answerQueryPayload);
        answerCollectionQueryBuilder.setPayload(answerQueryPayload);
        BuildQueryStrategy answerStrategy = operator.getQueryStrategy();
        answerCollectionQueryBuilder.setPayload(answerQueryPayload);
        answerStrategy.setBaseQueryBuilder(answerCollectionQueryBuilder);

        NestedQueryStrategy nestedQueryStrategy = new NestedQueryStrategy("activities.questionsAnswers");
        nestedQueryStrategy.addStrategy(stableIdQueryStrategy, answerStrategy);
        nestedQueryStrategy.setBaseQueryBuilder(stableIdCollectionQueryBuilder);
        return nestedQueryStrategy.build();
    }
}
