package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.Arrays;
import java.util.List;

import org.broadinstitute.dsm.model.elastic.MockFieldTypeExtractor;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
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
                (NestedQueryBuilder) baseQueryBuilder.build(queryStrategy.build());
        MatchQueryBuilder expectedMatchQueryBuilder = new MatchQueryBuilder("dsm.medicalRecord.followupRequired", "1");
        Assert.assertEquals(expectedMatchQueryBuilder, queryBuilder.query());
    }

    @Test
    public void nonExactMatchWithSpaces() {
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

        List<QueryBuilder> actualWildCardQueries = queryStrategy.build();

        Assert.assertEquals(2, actualWildCardQueries.size());

        List<WildcardQueryBuilder> expectedWildCardQueries = Arrays.asList(new WildcardQueryBuilder("dsm.medicalRecord.notes", "*test*"),
                new WildcardQueryBuilder("dsm.medicalRecord.notes", "*note*"));

        Assert.assertEquals(expectedWildCardQueries, actualWildCardQueries);
    }

}
