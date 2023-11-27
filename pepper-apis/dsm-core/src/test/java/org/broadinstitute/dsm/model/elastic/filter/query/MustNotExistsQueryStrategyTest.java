package org.broadinstitute.dsm.model.elastic.filter.query;

import org.apache.commons.lang3.StringUtils;
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
        QueryPayload followupRequiredText =
                new QueryPayload("dsm.medicalRecord", "followupRequiredText", "m",
                         new Boolean[] {true}, StringUtils.EMPTY);
        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of(followupRequiredText);
        BuildQueryStrategy queryStrategy = isNull.getQueryStrategy();
        queryStrategy.setBaseQueryBuilder(baseQueryBuilder);
        BoolQueryBuilder queryBuilder = (BoolQueryBuilder)
                ((NestedQueryBuilder)baseQueryBuilder.build(queryStrategy.build())).query();
        BoolQueryBuilder expectedBoolQuery = new BoolQueryBuilder();
        expectedBoolQuery.mustNot(new ExistsQueryBuilder("dsm.medicalRecord.followupRequiredText"));
        Assert.assertEquals(expectedBoolQuery.mustNot().get(0), queryBuilder.mustNot().get(0));
    }
}
