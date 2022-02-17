package org.broadinstitute.dsm.model.elastic.filter.query;

import org.apache.lucene.search.join.ScoreMode;
import org.broadinstitute.dsm.model.elastic.filter.FilterParser;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.broadinstitute.dsm.model.elastic.filter.splitter.JsonContainsSplitter;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.junit.Assert;
import org.junit.Test;

public class TestResultCollectionQueryBuilderTest {


    @Test
    public void build() {
        BaseQueryBuilder queryBuilder = new TestResultCollectionQueryBuilder();
        QueryPayload queryPayload =
                new QueryPayload("dsm.kitRequestShipping", "testResult.isCorrected", new FilterParser().parse(new String[] {"'true'"}));
        String filter = "JSON_CONTAINS(k.test_result, JSON_OBJECT('isCorrected', 'true'))";
        JsonContainsSplitter splitter = new JsonContainsSplitter();
        splitter.setFilter(filter);
        QueryBuilder query = queryBuilder.buildEachQuery(Operator.JSON_CONTAINS, queryPayload, splitter);
        MatchQueryBuilder matchQueryBuilder = new MatchQueryBuilder("dsm.kitRequestShipping.testResult.isCorrected", true);
        NestedQueryBuilder expected = new NestedQueryBuilder("dsm.kitRequestShipping.testResult", matchQueryBuilder, ScoreMode.Avg);

        Assert.assertEquals(expected, query);
    }

}
