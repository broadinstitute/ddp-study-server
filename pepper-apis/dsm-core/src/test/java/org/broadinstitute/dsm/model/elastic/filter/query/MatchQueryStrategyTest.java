package org.broadinstitute.dsm.model.elastic.filter.query;


import org.broadinstitute.dsm.model.elastic.MockFieldTypeExtractor;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
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
        QueryPayload duplicatePayload = new QueryPayload("dsm.medicalRecord", "duplicate", new Boolean[] {true});
        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of("m", "duplicate");
        NestedQueryBuilder queryBuilder = (NestedQueryBuilder) baseQueryBuilder.buildEachQuery(equals, duplicatePayload);
        MatchQueryBuilder expectedMatchQueryBuilder = new MatchQueryBuilder("dsm.medicalRecord.duplicate", true);
        Assert.assertEquals(expectedMatchQueryBuilder, queryBuilder.query());
    }
    @Test
    public void exactMatchQueryBuildText() {
        QueryPayload duplicatePayload = new QueryPayload("dsm.medicalRecord", "notes", new String[] {"test note"});
        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of("m", "notes");
        NestedQueryBuilder queryBuilder = (NestedQueryBuilder) baseQueryBuilder.buildEachQuery(equals, duplicatePayload);
        MatchQueryBuilder expectedMatchQueryBuilder = new MatchQueryBuilder("dsm.medicalRecord.notes.keyword", "test note");
        Assert.assertEquals(expectedMatchQueryBuilder, queryBuilder.query());
    }
    @Test
    public void exactMatchQueryBuildDate() {
        QueryPayload duplicatePayload = new QueryPayload("dsm.medicalRecord", "faxSent", new String[] {"09/22/2020"});
        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of("m", "faxSent");
        NestedQueryBuilder queryBuilder = (NestedQueryBuilder) baseQueryBuilder.buildEachQuery(equals, duplicatePayload);
        MatchQueryBuilder expectedMatchQueryBuilder = new MatchQueryBuilder("dsm.medicalRecord.faxSent", "09/22/2020");
        Assert.assertEquals(expectedMatchQueryBuilder, queryBuilder.query());
    }
    @Test
    public void exactMatchQueryBuildNumeric() {
        QueryPayload duplicatePayload = new QueryPayload("dsm.tissue", "ussCount", new String[] {"5"});
        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of("t", "ussCount");
        NestedQueryBuilder queryBuilder = (NestedQueryBuilder) baseQueryBuilder.buildEachQuery(equals, duplicatePayload);
        MatchQueryBuilder expectedMatchQueryBuilder = new MatchQueryBuilder("dsm.tissue.ussCount", "5");
        Assert.assertEquals(expectedMatchQueryBuilder, queryBuilder.query());
    }
}