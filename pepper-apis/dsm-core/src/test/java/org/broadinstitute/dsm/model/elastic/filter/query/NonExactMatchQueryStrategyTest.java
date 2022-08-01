package org.broadinstitute.dsm.model.elastic.filter.query;

import org.broadinstitute.dsm.model.elastic.MockFieldTypeExtractor;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.WildcardQueryBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class NonExactMatchQueryStrategyTest {

    Operator like;

    @Before
    public void setUp() {
        like = Operator.LIKE;
        like.getQueryStrategy().setExtractor(new MockFieldTypeExtractor());
    }

    @Test
    public void likeMatchQueryBuildText() {
        QueryPayload duplicatePayload =
                new QueryPayload.Builder()
                        .withPath("dsm.medicalRecord")
                        .withProperty("notes")
                        .withValues(new String[] {"test note"})
                        .withAlias("m")
                        .build();
        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of(duplicatePayload);
        BuildQueryStrategy queryStrategy = like.getQueryStrategy();
        queryStrategy.setBaseQueryBuilder(baseQueryBuilder);

        NestedQueryBuilder queryBuilder =
                (NestedQueryBuilder) baseQueryBuilder.buildEachQuery(queryStrategy.build(), duplicatePayload);
        WildcardQueryBuilder expectedMatchQueryBuilder = new WildcardQueryBuilder("dsm.medicalRecord.notes", "*test note*");
        Assert.assertEquals(expectedMatchQueryBuilder, queryBuilder.query());
    }

    @Test
    public void likeMatchQueryBuildNonText() {
        QueryPayload duplicatePayload =
                        new QueryPayload.Builder()
                                .withPath("dsm.medicalRecord")
                                .withProperty("followupRequired")
                                .withValues(new String[] {"1"})
                                .withAlias("m")
                                .build();
        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of(duplicatePayload);
        BuildQueryStrategy queryStrategy = like.getQueryStrategy();
        queryStrategy.setBaseQueryBuilder(baseQueryBuilder);
        NestedQueryBuilder queryBuilder =
                (NestedQueryBuilder) baseQueryBuilder.buildEachQuery(queryStrategy.build(), duplicatePayload);
        MatchQueryBuilder expectedMatchQueryBuilder = new MatchQueryBuilder("dsm.medicalRecord.followupRequired", "1");
        Assert.assertEquals(expectedMatchQueryBuilder, queryBuilder.query());
    }


}
