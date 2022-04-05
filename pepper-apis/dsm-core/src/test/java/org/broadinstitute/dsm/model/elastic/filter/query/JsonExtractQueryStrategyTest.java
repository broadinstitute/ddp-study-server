package org.broadinstitute.dsm.model.elastic.filter.query;

import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.elasticsearch.index.query.*;
import org.jdbi.v3.core.mapper.Nested;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class JsonExtractQueryStrategyTest {

    @Test
    public void buildIsNotEmpty() {
        String filter = "JSON_EXTRACT ( m.additional_values_json , '$.hi' )  IS NOT NULL";
        Operator jsonExtract = Operator.extract(filter);
        QueryPayload payload = new QueryPayload("dsm.medicalRecord", "dynamicFields.hi", new String[] {""});

        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of("m", "dynamicFields.hi");

        BoolQueryBuilder boolQueryBuilder = (BoolQueryBuilder) baseQueryBuilder.buildEachQuery(jsonExtract, payload);

        ExistsQueryBuilder expected = new ExistsQueryBuilder("dsm.medicalRecord.dynamicFields.hi");

        Assert.assertEquals(expected, ((NestedQueryBuilder) boolQueryBuilder.must().get(0)).query());
    }

    @Test
    public void buildIsEmpty() {
        String filter = "JSON_EXTRACT ( m.additional_values_json , '$.hi' )  IS NULL";
        Operator jsonExtract = Operator.extract(filter);
        QueryPayload payload = new QueryPayload("dsm.medicalRecord", "dynamicFields.hi", new String[] {""});

        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of("m", "dynamicFields.hi");

        BoolQueryBuilder boolQueryBuilder = (BoolQueryBuilder) baseQueryBuilder.buildEachQuery(jsonExtract, payload);

        ExistsQueryBuilder expected = new ExistsQueryBuilder("dsm.medicalRecord.dynamicFields.hi");

        Assert.assertEquals(expected, ((NestedQueryBuilder) boolQueryBuilder.mustNot().get(0)).query());
    }

    @Test
    public void buildEquals() {
        String filter = "JSON_EXTRACT ( m.additional_values_json , '$.numberTest' )  = 100";
        Operator jsonExtract = Operator.extract(filter);
        QueryPayload payload = new QueryPayload("dsm.medicalRecord", "dynamicFields.numberTest", new Integer[] {100});

        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of("m", "dynamicFields.numberTest");

        NestedQueryBuilder nestedQueryBuilder = (NestedQueryBuilder) baseQueryBuilder.buildEachQuery(jsonExtract, payload);

        MatchQueryBuilder expected = new MatchQueryBuilder("dsm.medicalRecord.dynamicFields.numberTest", 100);

        Assert.assertEquals(expected, nestedQueryBuilder.query());
    }


}