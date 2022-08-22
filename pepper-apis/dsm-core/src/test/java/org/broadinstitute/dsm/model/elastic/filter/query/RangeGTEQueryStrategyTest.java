package org.broadinstitute.dsm.model.elastic.filter.query;

import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.junit.Assert;
import org.junit.Test;

public class RangeGTEQueryStrategyTest {

    @Test
    public void build() {
        Operator dateGreaterThanEquals = Operator.DATE_GREATER_THAN_EQUALS;
        String[] values = {"2000-01-01"};
        QueryPayload mrDocument =
                new QueryPayload.Builder()
                        .withPath("dsm.medicalRecord")
                        .withProperty("mrReceived")
                        .withValues(values)
                        .withAlias("m")
                        .build();

        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of(mrDocument);
        BuildQueryStrategy queryStrategy = dateGreaterThanEquals.getQueryStrategy();
        queryStrategy.setBaseQueryBuilder(baseQueryBuilder);
        NestedQueryBuilder nestedGreaterQuery =
                (NestedQueryBuilder) baseQueryBuilder.build(queryStrategy.build()
                );

        RangeQueryBuilder expected = new RangeQueryBuilder("dsm.medicalRecord.mrReceived");
        expected.gte(values[0]);

        Assert.assertEquals(expected, nestedGreaterQuery.query());
    }
}
