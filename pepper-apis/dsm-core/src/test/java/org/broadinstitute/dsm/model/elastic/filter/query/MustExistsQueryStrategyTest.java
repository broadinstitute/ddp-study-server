package org.broadinstitute.dsm.model.elastic.filter.query;


import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.junit.Assert;
import org.junit.Test;

public class MustExistsQueryStrategyTest {

    @Test
    public void mustExistsQueryBuild() {
        Operator isNotNull = Operator.IS_NOT_NULL;
        QueryPayload followupRequiredText = new QueryPayload("dsm.medicalRecord", "followupRequiredText", new Boolean[] {true});
        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of("m", "followupRequiredText");
        BoolQueryBuilder queryBuilder = (BoolQueryBuilder) baseQueryBuilder.buildEachQuery(isNotNull, followupRequiredText);
        BoolQueryBuilder expectedBoolQuery = new BoolQueryBuilder();
        expectedBoolQuery.must(new ExistsQueryBuilder("dsm.medicalRecord.followupRequiredText"));
        Assert.assertEquals(expectedBoolQuery.must().get(0), ((NestedQueryBuilder)queryBuilder.must().get(0)).query());
    }
}