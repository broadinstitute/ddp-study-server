package org.broadinstitute.dsm.model.elastic.filter.query;

import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.junit.Assert;
import org.junit.Test;

public class MustNotExistsQueryStrategyTest {

    @Test
    public void buildMustNotExistsQuery() {
        Operator isNull = Operator.IS_NULL;
        QueryPayload followupRequiredText = new QueryPayload("dsm.medicalRecord", "followupRequiredText", new Boolean[] {true});
        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of("m", "followupRequiredText");
        BoolQueryBuilder queryBuilder = (BoolQueryBuilder) baseQueryBuilder.buildEachQuery(isNull, followupRequiredText);
        BoolQueryBuilder expectedBoolQuery = new BoolQueryBuilder();
        expectedBoolQuery.mustNot(new ExistsQueryBuilder("dsm.medicalRecord.followupRequiredText"));
        Assert.assertEquals(expectedBoolQuery.mustNot().get(0), ((NestedQueryBuilder)queryBuilder.mustNot().get(0)).query());
    }
}