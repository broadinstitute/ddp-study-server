package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.List;

import org.broadinstitute.dsm.model.elastic.MockFieldTypeExtractor;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class MatchQueryStrategyTest {

    Operator equals;

    @Before
    public void setUp() {
        equals = Operator.EQUALS;
        equals.getQueryStrategy().setExtractor(new MockFieldTypeExtractor());

    }

    @Test
    public void matchQueryBuildBoolean() {
        QueryPayload payload =
                new QueryPayload.Builder()
                        .withPath("dsm.medicalRecord")
                        .withProperty("duplicate")
                        .withValues(new Boolean[] {true})
                        .withAlias("m")
                        .build();
        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of(payload);
        NestedQueryBuilder queryBuilder =
                (NestedQueryBuilder)
                        baseQueryBuilder.build(getQueryBuilders(payload, baseQueryBuilder));
        MatchQueryBuilder expectedMatchQueryBuilder = new MatchQueryBuilder("dsm.medicalRecord.duplicate", true).operator(
                org.elasticsearch.index.query.Operator.AND);
        Assert.assertEquals(expectedMatchQueryBuilder, queryBuilder.query());
    }

    @Test
    public void exactMatchQueryBuildText() {
        QueryPayload payload =
                new QueryPayload.Builder()
                        .withPath("dsm.medicalRecord")
                        .withProperty("notes")
                        .withValues(new String[] {"test note"})
                        .withAlias("m")
                        .build();
        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of(payload);
        NestedQueryBuilder queryBuilder =
                (NestedQueryBuilder)
                        baseQueryBuilder.build(getQueryBuilders(payload, baseQueryBuilder));
        MatchQueryBuilder expectedMatchQueryBuilder = new MatchQueryBuilder("dsm.medicalRecord.notes.keyword", "test note");
        expectedMatchQueryBuilder.operator(org.elasticsearch.index.query.Operator.AND);
        Assert.assertEquals(expectedMatchQueryBuilder, queryBuilder.query());
    }

    @Test
    public void exactMatchQueryBuildDate() {
        QueryPayload payload =
                new QueryPayload.Builder()
                        .withPath("dsm.medicalRecord")
                        .withProperty("faxSent")
                        .withValues(new String[] {"09/22/2020"})
                        .withAlias("m")
                        .build();
        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of(payload);
        NestedQueryBuilder queryBuilder =
                (NestedQueryBuilder)
                        baseQueryBuilder.build(getQueryBuilders(payload, baseQueryBuilder));
        MatchQueryBuilder expectedMatchQueryBuilder = new MatchQueryBuilder("dsm.medicalRecord.faxSent", "09/22/2020");
        expectedMatchQueryBuilder.operator(org.elasticsearch.index.query.Operator.AND);
        Assert.assertEquals(expectedMatchQueryBuilder, queryBuilder.query());
    }

    @Test
    public void exactMatchQueryBuildNumeric() {
        QueryPayload payload =
                new QueryPayload.Builder()
                        .withPath("dsm.tissue")
                        .withProperty("ussCount")
                        .withValues(new String[] {"5"})
                        .withAlias("t")
                        .build();
        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of(payload);
        NestedQueryBuilder queryBuilder =
                (NestedQueryBuilder)
                        baseQueryBuilder.build(getQueryBuilders(payload, baseQueryBuilder));
        MatchQueryBuilder expectedMatchQueryBuilder = new MatchQueryBuilder("dsm.tissue.ussCount", "5");
        expectedMatchQueryBuilder.operator(org.elasticsearch.index.query.Operator.AND);
        Assert.assertEquals(expectedMatchQueryBuilder, queryBuilder.query());
    }

    private List<QueryBuilder> getQueryBuilders(QueryPayload duplicatePayload, BaseQueryBuilder baseQueryBuilder) {
        baseQueryBuilder.payload = duplicatePayload;
        BuildQueryStrategy queryStrategy = equals.getQueryStrategy();
        queryStrategy.setBaseQueryBuilder(baseQueryBuilder);
        return queryStrategy.build();
    }
}
