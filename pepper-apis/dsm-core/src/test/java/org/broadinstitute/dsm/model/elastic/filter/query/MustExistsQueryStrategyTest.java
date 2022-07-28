package org.broadinstitute.dsm.model.elastic.filter.query;

import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.junit.Assert;
import org.junit.Test;

public class MustExistsQueryStrategyTest {

    @Test
    public void mustExistsQueryBuild() {
        Operator isNotNull = Operator.IS_NOT_NULL;
        QueryPayload followupRequiredText = new QueryPayload("dsm.medicalRecord", "followupRequiredText", new Boolean[] {true});
        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of("m", "followupRequiredText");
        BoolQueryBuilder queryBuilder = (BoolQueryBuilder) ((NestedQueryBuilder)baseQueryBuilder.buildEachQuery(isNotNull,
                followupRequiredText)).query();
        BoolQueryBuilder expectedBoolQuery = new BoolQueryBuilder();
        expectedBoolQuery.must(new ExistsQueryBuilder("dsm.medicalRecord.followupRequiredText"));
        Assert.assertEquals(expectedBoolQuery.must().get(0), queryBuilder.must().get(0));
    }
}
