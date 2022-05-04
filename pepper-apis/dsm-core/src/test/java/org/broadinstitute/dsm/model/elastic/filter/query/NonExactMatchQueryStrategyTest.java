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
        QueryPayload duplicatePayload = new QueryPayload("dsm.medicalRecord", "notes", new String[] {"test note"});
        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of("m", "notes");
        NestedQueryBuilder queryBuilder = (NestedQueryBuilder) baseQueryBuilder.buildEachQuery(like, duplicatePayload);
        WildcardQueryBuilder expectedMatchQueryBuilder = new WildcardQueryBuilder("dsm.medicalRecord.notes", "*test note*");
        Assert.assertEquals(expectedMatchQueryBuilder, queryBuilder.query());
    }

    @Test
    public void likeMatchQueryBuildNonText() {
        QueryPayload duplicatePayload = new QueryPayload("dsm.medicalRecord", "followupRequired", new String[] {"1"});
        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of("m", "followupRequired");
        NestedQueryBuilder queryBuilder = (NestedQueryBuilder) baseQueryBuilder.buildEachQuery(like, duplicatePayload);
        MatchQueryBuilder expectedMatchQueryBuilder = new MatchQueryBuilder("dsm.medicalRecord.followupRequired", "1");
        Assert.assertEquals(expectedMatchQueryBuilder, queryBuilder.query());
    }


}
