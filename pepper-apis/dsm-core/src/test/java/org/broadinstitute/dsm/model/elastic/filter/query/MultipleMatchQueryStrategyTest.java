package org.broadinstitute.dsm.model.elastic.filter.query;

import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.junit.Assert;
import org.junit.Test;

public class MultipleMatchQueryStrategyTest {

    @Test
    public void build() {
        Operator multipleOptions = Operator.MULTIPLE_OPTIONS;
        String[] values = {"Full", "Partial"};
        QueryPayload mrDocument =
                new QueryPayload.Builder()
                        .withPath("dsm.medicalRecord")
                        .withProperty("mrDocument")
                        .withValues(values)
                        .withAlias("m")
                        .build();

        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of(mrDocument);
        BuildQueryStrategy queryStrategy = multipleOptions.getQueryStrategy();
        queryStrategy.setBaseQueryBuilder(baseQueryBuilder);
        NestedQueryBuilder nestedQueryBuilder =
                (NestedQueryBuilder) baseQueryBuilder.build(queryStrategy.build()
                );

        BoolQueryBuilder expectedBoolQuery = new BoolQueryBuilder();

        expectedBoolQuery.should(new MatchQueryBuilder("dsm.medicalRecord.mrDocument", "Full"));
        expectedBoolQuery.should(new MatchQueryBuilder("dsm.medicalRecord.mrDocument", "Partial"));

        Assert.assertEquals(expectedBoolQuery.should().get(0), ((BoolQueryBuilder) nestedQueryBuilder.query()).should().get(0));
        Assert.assertEquals(expectedBoolQuery.should().get(1), ((BoolQueryBuilder) nestedQueryBuilder.query()).should().get(1));
    }
}
