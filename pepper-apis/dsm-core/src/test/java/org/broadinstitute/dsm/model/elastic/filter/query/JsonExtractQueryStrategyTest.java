package org.broadinstitute.dsm.model.elastic.filter.query;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.filter.AndOrFilterSeparator;
import org.broadinstitute.dsm.model.elastic.filter.FilterParser;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.broadinstitute.dsm.model.elastic.filter.splitter.SplitterStrategy;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.junit.Assert;
import org.junit.Test;

public class JsonExtractQueryStrategyTest {

    @Test
    public void buildIsNotEmpty() {
        String filter = "JSON_EXTRACT ( m.additional_values_json , '$.hi' )  IS NOT NULL";
        Operator jsonExtract = Operator.extract(filter);
        QueryPayload payload =
                new QueryPayload("dsm.medicalRecord", "dynamicFields.hi", "m", new String[] {""}, StringUtils.EMPTY);

        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of(payload);

        BuildQueryStrategy queryStrategy = jsonExtract.getQueryStrategy();
        baseQueryBuilder.operator = jsonExtract;
        queryStrategy.setBaseQueryBuilder(baseQueryBuilder);
        BoolQueryBuilder boolQueryBuilder = (BoolQueryBuilder)
                ((NestedQueryBuilder)baseQueryBuilder.build(queryStrategy.build())).query();

        ExistsQueryBuilder expected = new ExistsQueryBuilder("dsm.medicalRecord.dynamicFields.hi");

        Assert.assertEquals(expected, boolQueryBuilder.must().get(0));
    }

    @Test
    public void buildIsEmpty() {
        String filter = "JSON_EXTRACT ( m.additional_values_json , '$.hi' )  IS NULL";
        Operator jsonExtract = Operator.extract(filter);
        QueryPayload payload =
                new QueryPayload("dsm.medicalRecord", "dynamicFields.hi", "m", new String[] {""}, StringUtils.EMPTY);

        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of(payload);

        BuildQueryStrategy queryStrategy = jsonExtract.getQueryStrategy();
        baseQueryBuilder.operator = jsonExtract;
        queryStrategy.setBaseQueryBuilder(baseQueryBuilder);
        BoolQueryBuilder boolQueryBuilder = (BoolQueryBuilder)
                ((NestedQueryBuilder)baseQueryBuilder.build(queryStrategy.build())).query();

        ExistsQueryBuilder expected = new ExistsQueryBuilder("dsm.medicalRecord.dynamicFields.hi");

        Assert.assertEquals(expected, boolQueryBuilder.mustNot().get(0));
    }

    @Test
    public void buildEquals() {
        String filter = "JSON_EXTRACT ( m.additional_values_json , '$.numberTest' ) = 100";
        Operator jsonExtract = Operator.extract(filter);
        SplitterStrategy splitterStrategy = jsonExtract.getSplitterStrategy();
        splitterStrategy.setFilterSeparator(new AndOrFilterSeparator(StringUtils.EMPTY));
        splitterStrategy.setFilter(filter);
        QueryPayload payload =
                new QueryPayload("dsm.medicalRecord", "dynamicFields.numberTest", "m",
                         new FilterParser().parse(splitterStrategy.getValue()), StringUtils.EMPTY);

        BaseQueryBuilder baseQueryBuilder = BaseQueryBuilder.of(payload);

        BuildQueryStrategy queryStrategy = jsonExtract.getQueryStrategy();
        baseQueryBuilder.operator = jsonExtract;
        queryStrategy.setBaseQueryBuilder(baseQueryBuilder);
        NestedQueryBuilder nestedQueryBuilder =
                (NestedQueryBuilder) baseQueryBuilder.build(queryStrategy.build());

        MatchQueryBuilder expected = new MatchQueryBuilder("dsm.medicalRecord.dynamicFields.numberTest", 100L).operator(
                org.elasticsearch.index.query.Operator.AND);
        Assert.assertEquals(expected, nestedQueryBuilder.query());
    }
}
