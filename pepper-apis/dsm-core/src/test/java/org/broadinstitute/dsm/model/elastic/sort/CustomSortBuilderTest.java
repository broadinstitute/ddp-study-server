package org.broadinstitute.dsm.model.elastic.sort;

import org.broadinstitute.dsm.model.elastic.MockFieldTypeExtractor;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.sort.NestedSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.Assert;
import org.junit.Test;

public class CustomSortBuilderTest {

    @Test
    public void buildNestedCustomSortBuilder() {

        SortBy sortBy = new SortBy.Builder()
                .withType("JSONARRAY")
                .withAdditionalType("CHECKBOX")
                .withOrder("ASC")
                .withOuterProperty("testResult")
                .withInnerProperty("isCorrected")
                .withTableAlias("k")
                .build();

        Sort sort = new Sort(sortBy, new MockFieldTypeExtractor());

        CustomSortBuilder customSortBuilder = new CustomSortBuilder(sort);

        NestedSortBuilder actualNestedSortPath = customSortBuilder.getNestedSort();
        NestedSortBuilder expectedNestedSortPath = new NestedSortBuilder("dsm.kitRequestShipping.testResult");

        Assert.assertEquals(expectedNestedSortPath, actualNestedSortPath);
    }

    @Test
    public void buildNestedQuestionsAnswersCustomSortBuilder() {

        SortBy sortBy = new SortBy.Builder()
                .withType("TEXT")
                .withOrder("ASC")
                .withInnerProperty("YEARS")
                .withOuterProperty("questionsAnswers")
                .withTableAlias("MEDICAL_HISTORY")
                .build();

        Sort sort = new Sort(sortBy, new MockFieldTypeExtractor());

        CustomSortBuilder customSortBuilder = new CustomSortBuilder(sort);

        NestedSortBuilder actualNestedSortPath = customSortBuilder.getNestedSort();
        NestedSortBuilder expectedNestedSortPath = new NestedSortBuilder("activities.questionsAnswers");

        Assert.assertEquals(expectedNestedSortPath, actualNestedSortPath);
    }

    @Test
    public void buildSingleActivitiesCustomSortBuilder() {

        SortBy sortBy = new SortBy.Builder()
                .withType("TEXT")
                .withOrder("ASC")
                .withInnerProperty("completedAt")
                .withTableAlias("MEDICAL_HISTORY")
                .withActivityVersions(new String[] {"v1", "v2"})
                .build();

        Sort sort = new Sort(sortBy, new MockFieldTypeExtractor());

        CustomSortBuilder customSortBuilder = new CustomSortBuilder(sort);

        NestedSortBuilder actualNestedSortPath = customSortBuilder.getNestedSort();
        NestedSortBuilder expectedNestedSortPath = new NestedSortBuilder("activities");

        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        boolQueryBuilder.must(new TermQueryBuilder("activities.activityCode", "MEDICAL_HISTORY"));
        boolQueryBuilder.must(new TermsQueryBuilder("activities.activityVersion", "v1", "v2"));
        expectedNestedSortPath.setFilter(boolQueryBuilder);

        Assert.assertEquals(expectedNestedSortPath, actualNestedSortPath);
        Assert.assertEquals(SortOrder.ASC, customSortBuilder.order());
    }
}