package org.broadinstitute.dsm.model.elastic.filter.query;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.junit.Assert;
import org.junit.Test;

public class RangeLTEQueryStrategyTest {

    @Test
    public void build() {
        Operator dateLessThanEquals = Operator.DATE_LESS_THAN_EQUALS;
        String[] values = {"2000-01-01"};
        QueryPayload mrDocument =
                new QueryPayload("dsm.medicalRecord", "mrReceived", "m", values, StringUtils.EMPTY);

        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of(mrDocument);

        BuildQueryStrategy queryStrategy = dateLessThanEquals.getQueryStrategy();
        queryStrategy.setBaseQueryBuilder(baseQueryBuilder);

        NestedQueryBuilder nestedGreaterQuery =
                (NestedQueryBuilder) baseQueryBuilder.build(queryStrategy.build()
                );

        RangeQueryBuilder expected = new RangeQueryBuilder("dsm.medicalRecord.mrReceived");
        expected.lte(values[0]);

        Assert.assertEquals(expected, nestedGreaterQuery.query());
    }
}
