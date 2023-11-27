package org.broadinstitute.dsm.model.elastic.filter.query;

import org.apache.lucene.search.join.ScoreMode;
import org.broadinstitute.dsm.model.elastic.filter.FilterParser;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.junit.Assert;
import org.junit.Test;

public class TestResultCollectionQueryBuilderTest {


    @Test
    public void build() {
        QueryPayload queryPayload =
                new QueryPayload.Builder()
                        .withPath("dsm.kitRequestShipping")
                        .withProperty("testResult.isCorrected")
                        .withValues(new FilterParser().parse(new String[] {"'true'"}))
                        .build();
        BaseQueryBuilder queryBuilder = new TestResultCollectionQueryBuilder(queryPayload);
        String filter = "JSON_CONTAINS(k.test_result, JSON_OBJECT('isCorrected', 'true'))";
        Operator.JSON_CONTAINS.getSplitterStrategy().setFilter(filter);
        BuildQueryStrategy queryStrategy = Operator.JSON_CONTAINS.getQueryStrategy();
        queryStrategy.setBaseQueryBuilder(queryBuilder);
        queryBuilder.setOperator(Operator.JSON_CONTAINS);
        QueryBuilder query = queryBuilder.build(queryStrategy.build());
        MatchQueryBuilder matchQueryBuilder = new MatchQueryBuilder("dsm.kitRequestShipping.testResult.isCorrected", true).operator(
                org.elasticsearch.index.query.Operator.AND);
        NestedQueryBuilder expected = new NestedQueryBuilder("dsm.kitRequestShipping.testResult", matchQueryBuilder, ScoreMode.Avg);

        Assert.assertEquals(expected, query);
    }

}
