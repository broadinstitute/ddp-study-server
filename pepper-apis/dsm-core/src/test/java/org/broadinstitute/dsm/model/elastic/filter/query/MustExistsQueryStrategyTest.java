package org.broadinstitute.dsm.model.elastic.filter.query;

import org.apache.commons.lang3.StringUtils;
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
        QueryPayload followupRequiredText =
                new QueryPayload("dsm.medicalRecord", "followupRequiredText",
                        "m", new Boolean[] {true}, StringUtils.EMPTY);
        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of(followupRequiredText);
        BuildQueryStrategy queryStrategy = isNotNull.getQueryStrategy();
        queryStrategy.setBaseQueryBuilder(baseQueryBuilder);
        BoolQueryBuilder queryBuilder =
                (BoolQueryBuilder) ((NestedQueryBuilder)baseQueryBuilder.build(queryStrategy.build()
                )).query();
        BoolQueryBuilder expectedBoolQuery = new BoolQueryBuilder();
        expectedBoolQuery.must(new ExistsQueryBuilder("dsm.medicalRecord.followupRequiredText"));
        Assert.assertEquals(expectedBoolQuery.must().get(0), queryBuilder.must().get(0));
    }
}
