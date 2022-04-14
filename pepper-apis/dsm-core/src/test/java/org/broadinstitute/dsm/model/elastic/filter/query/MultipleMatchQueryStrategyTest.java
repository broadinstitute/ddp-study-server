package org.broadinstitute.dsm.model.elastic.filter.query;

import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class MultipleMatchQueryStrategyTest {

    @Test
    public void build() {
        Operator multipleOptions = Operator.MULTIPLE_OPTIONS;
        String[] values = {"Full", "Partial"};
        QueryPayload mrDocument = new QueryPayload("dsm.medicalRecord", "mrDocument", values);

        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of("m", "mrDocument");
        NestedQueryBuilder nestedQueryBuilder = (NestedQueryBuilder) baseQueryBuilder.buildEachQuery(multipleOptions, mrDocument);

        BoolQueryBuilder expectedBoolQuery = new BoolQueryBuilder();

        expectedBoolQuery.should(new MatchQueryBuilder("dsm.medicalRecord.mrDocument", "Full"));
        expectedBoolQuery.should(new MatchQueryBuilder("dsm.medicalRecord.mrDocument", "Partial"));

        Assert.assertEquals(expectedBoolQuery.should().get(0), ((BoolQueryBuilder) nestedQueryBuilder.query()).should().get(0));
        Assert.assertEquals(expectedBoolQuery.should().get(1), ((BoolQueryBuilder) nestedQueryBuilder.query()).should().get(1));
    }
}