package org.broadinstitute.dsm.model.elastic.filter.query;

import org.apache.lucene.search.join.ScoreMode;
import org.broadinstitute.dsm.model.elastic.filter.FilterParser;
import org.broadinstitute.dsm.model.elastic.filter.FilterSeparatorFactory;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ActivitiesCollectionQueryBuilderTest {

    public static final String ES = "ES";
    private static AbstractQueryBuilderFactory builderFactory;
    private static BaseAbstractQueryBuilder abstractQueryBuilder;
    private static FilterSeparatorFactory factory;

    @BeforeClass
    public static void setUp() {
        builderFactory = new AbstractQueryBuilderFactory(ES);
        abstractQueryBuilder = new BaseAbstractQueryBuilder();
        factory = new FilterSeparatorFactory();
        factory.setAlias(ES);
    }


    @Test
    public void activitiesCompletedAt() {
        String filter = " AND ANGIORELEASE.completedAt IS NOT NULL ";
        factory.setFilter(filter);
        abstractQueryBuilder.setFilterSeparator(factory.create());
        abstractQueryBuilder.setFilter(filter);
        abstractQueryBuilder.setParser(new FilterParser());
        AbstractQueryBuilder<?> actual = abstractQueryBuilder.build();
        BoolQueryBuilder expected = new BoolQueryBuilder();
        BoolQueryBuilder nestedBoolQuery = new BoolQueryBuilder();
        nestedBoolQuery.must(new MatchQueryBuilder("activities.activityCode", "ANGIORELEASE"));
        nestedBoolQuery.must(new BoolQueryBuilder().must(new ExistsQueryBuilder("activities.completedAt")));
        NestedQueryBuilder expectedNestedQuery = new NestedQueryBuilder("activities", nestedBoolQuery, ScoreMode.Avg);
        expected.must(expectedNestedQuery);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void activitiesCreatedAt() {
        String filter = " AND ANGIORELEASE.completedAt = '2022-07-28'";
        factory.setFilter(filter);
        abstractQueryBuilder.setFilterSeparator(factory.create());
        abstractQueryBuilder.setFilter(filter);
        abstractQueryBuilder.setParser(new FilterParser());
        AbstractQueryBuilder<?> actual = abstractQueryBuilder.build();
        BoolQueryBuilder expected = new BoolQueryBuilder();
        BoolQueryBuilder nestedBoolQuery = new BoolQueryBuilder();
        nestedBoolQuery.must(new MatchQueryBuilder("activities.activityCode", "ANGIORELEASE"));
        nestedBoolQuery.must(new MatchQueryBuilder("activities.completedAt", 1658966400000L));
        NestedQueryBuilder expectedNestedQuery = new NestedQueryBuilder("activities", nestedBoolQuery, ScoreMode.Avg);
        expected.must(expectedNestedQuery);
        Assert.assertEquals(expected, actual);
    }
}
