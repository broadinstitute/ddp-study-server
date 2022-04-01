package org.broadinstitute.dsm.model.elastic.filter.query;


import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.junit.Assert;
import org.junit.Test;

public class MatchQueryStrategyTest {

    @Test
    public void matchQueryBuild() {
        Operator like = Operator.LIKE;
        QueryPayload duplicatePayload = new QueryPayload("dsm.medicalRecord", "duplicate", new Boolean[] {true});
        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of("m", "duplicate");
        NestedQueryBuilder queryBuilder = (NestedQueryBuilder) baseQueryBuilder.buildEachQuery(like, duplicatePayload);
        MatchQueryBuilder expectedMatchQueryBuilder = new MatchQueryBuilder("dsm.medicalRecord.duplicate", true);
        Assert.assertEquals(expectedMatchQueryBuilder, queryBuilder.query());
    }
}