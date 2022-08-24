package org.broadinstitute.dsm.model.elastic.filter.query;

import org.apache.lucene.search.join.ScoreMode;
import org.broadinstitute.dsm.model.elastic.filter.FilterParser;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.junit.Assert;
import org.junit.Test;

public class ActivitiesCollectionQueryBuilderTest {

    public static final String ES = "ES";
    private BaseAbstractQueryBuilder abstractQueryBuilder;


    @Test
    public void activitiesCompletedAtNotEmpty() {
        String filter = " AND ANGIORELEASE.completedAt IS NOT NULL ";
        abstractQueryBuilder = AbstractQueryBuilderFactory.create(ES, filter);
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
    public void activitiesCreatedAtExactDate() {
        String filter = " AND ANGIORELEASE.completedAt = '2022-07-28'";
        abstractQueryBuilder = AbstractQueryBuilderFactory.create(ES, filter);
        abstractQueryBuilder.setParser(new FilterParser());
        AbstractQueryBuilder<?> actual = abstractQueryBuilder.build();
        BoolQueryBuilder expected = new BoolQueryBuilder();
        BoolQueryBuilder nestedBoolQuery = new BoolQueryBuilder();
        nestedBoolQuery.must(new MatchQueryBuilder("activities.activityCode", "ANGIORELEASE"));
        nestedBoolQuery.must(new MatchQueryBuilder("activities.completedAt", "2022-07-28"));
        NestedQueryBuilder expectedNestedQuery = new NestedQueryBuilder("activities", nestedBoolQuery, ScoreMode.Avg);
        expected.must(expectedNestedQuery);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void activitiesStatusOptions() {
        String filter = "  AND ( ANGIORELEASE.status = 'COMPLETE' OR ANGIORELEASE.status = 'IN_PROGRESS' ) ";
        abstractQueryBuilder = AbstractQueryBuilderFactory.create(ES, filter);
        abstractQueryBuilder.setParser(new FilterParser());
        AbstractQueryBuilder<?> actual = abstractQueryBuilder.build();
        BoolQueryBuilder expected = new BoolQueryBuilder();
        BoolQueryBuilder nestedBoolQuery = new BoolQueryBuilder();
        nestedBoolQuery.must(new MatchQueryBuilder("activities.activityCode", "ANGIORELEASE"));
        nestedBoolQuery.must(new BoolQueryBuilder()
                .should(new MatchQueryBuilder("activities.status", "COMPLETE"))
                .should(new MatchQueryBuilder("activities.status", "IN_PROGRESS"))
        );
        NestedQueryBuilder expectedNestedQuery = new NestedQueryBuilder("activities", nestedBoolQuery, ScoreMode.Avg);
        expected.must(expectedNestedQuery);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void activitiesLastUpdatedAtRange() {
        String filter = " AND ANGIORELEASE.lastUpdatedAt  >= '2022-04-21' AND ANGIORELEASE.lastUpdatedAt  <= '2022-07-28'";
        abstractQueryBuilder = AbstractQueryBuilderFactory.create(ES, filter);
        abstractQueryBuilder.setParser(new FilterParser());
        AbstractQueryBuilder<?> actual = abstractQueryBuilder.build();
        BoolQueryBuilder expected = new BoolQueryBuilder();
        BoolQueryBuilder nestedBoolQuery = new BoolQueryBuilder();
        nestedBoolQuery.must(new MatchQueryBuilder("activities.activityCode", "ANGIORELEASE"));
        nestedBoolQuery.must(new RangeQueryBuilder("activities.lastUpdatedAt").gte("2022-04-21"));
        NestedQueryBuilder expectedNestedQuery = new NestedQueryBuilder("activities", nestedBoolQuery, ScoreMode.Avg);
        expected.must(expectedNestedQuery);

        BoolQueryBuilder nestedBoolQuery2 = new BoolQueryBuilder();
        nestedBoolQuery2.must(new MatchQueryBuilder("activities.activityCode", "ANGIORELEASE"));
        nestedBoolQuery2.must(new RangeQueryBuilder("activities.lastUpdatedAt").lte("2022-07-28"));
        NestedQueryBuilder expectedNestedQuery2 = new NestedQueryBuilder("activities", nestedBoolQuery2, ScoreMode.Avg);
        expected.must(expectedNestedQuery2);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void activitiesQuestionsAnswers() {
        String filter = " AND ANGIORELEASE.INSTITUTION = 'bla'";
        abstractQueryBuilder = AbstractQueryBuilderFactory.create(ES, filter);
        abstractQueryBuilder.setParser(new FilterParser());
        AbstractQueryBuilder<?> actual = abstractQueryBuilder.build();
        BoolQueryBuilder expected = new BoolQueryBuilder();
        BoolQueryBuilder nestedBoolQuery = new BoolQueryBuilder();
        nestedBoolQuery.must(new MatchQueryBuilder("activities.activityCode", "ANGIORELEASE"));
        BoolQueryBuilder innerNestedBoolQueryBuilder = new BoolQueryBuilder();
        innerNestedBoolQueryBuilder.must(new MatchQueryBuilder("activities.questionsAnswers.stableId", "INSTITUTION"));
        innerNestedBoolQueryBuilder.must(new MatchQueryBuilder("activities.questionsAnswers.answer", "bla"));
        NestedQueryBuilder innerNestedQueryBuilder =
                new NestedQueryBuilder("activities.questionsAnswers", innerNestedBoolQueryBuilder, ScoreMode.Avg);
        nestedBoolQuery.must(innerNestedQueryBuilder);
        NestedQueryBuilder expectedNestedQuery = new NestedQueryBuilder("activities", nestedBoolQuery, ScoreMode.Avg);
        expected.must(expectedNestedQuery);
        Assert.assertEquals(expected, actual);
    }
}
