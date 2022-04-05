package org.broadinstitute.dsm.model.elastic.filter.query;

import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class RangeGTEQueryStrategyTest {

    @Test
    public void build() {
        Operator dateGreaterThanEquals = Operator.DATE_GREATER_THAN_EQUALS;
        String[] values = {"2000-01-01"};
        QueryPayload mrDocument = new QueryPayload("dsm.medicalRecord", "mrReceived", values);

        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of("m", "mrReceived");
        NestedQueryBuilder nestedGreaterQuery = (NestedQueryBuilder) baseQueryBuilder.buildEachQuery(dateGreaterThanEquals, mrDocument);

        RangeQueryBuilder expected = new RangeQueryBuilder("dsm.medicalRecord.mrReceived");
        expected.gte(values[0]);

        Assert.assertEquals(expected, nestedGreaterQuery.query());
    }
}