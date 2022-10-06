package org.broadinstitute.dsm.model.dashboard;

import static org.junit.Assert.assertEquals;

import org.apache.lucene.search.join.ScoreMode;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelDto;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelFilterDto;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.Test;

public class CountAdditionalFilterStrategyTest {


    @Test
    public void processNonNestedSingleAdditionalFilter() {
        DashboardLabelFilterDto labelFilterDto = new DashboardLabelFilterDto.Builder()
                .withEsFilterPath("profile.createdAt")
                .withAdditionalFilter("AND profile.createdAt IS NOT NULL")
                .build();
        DashboardLabelDto labelDto = new DashboardLabelDto.Builder()
                .withDashboardLabelFilter(labelFilterDto)
                .build();
        QueryBuildPayload queryBuildPayload = new QueryBuildPayload(new DDPInstanceDto.Builder().build(), DisplayType.COUNT, labelDto);
        CountAdditionalFilterStrategy countAdditionalFilterStrategy = new CountAdditionalFilterStrategy(queryBuildPayload);
        BoolQueryBuilder actualQuery = (BoolQueryBuilder) countAdditionalFilterStrategy.process().must().get(0);

        BoolQueryBuilder expectedQuery = new BoolQueryBuilder();
        expectedQuery.must(QueryBuilders.existsQuery("profile.createdAt"));

        assertEquals(expectedQuery, actualQuery);
    }

    @Test
    public void processNonNestedMultipleAdditionalFilter() {
        DashboardLabelFilterDto labelFilterDto = new DashboardLabelFilterDto.Builder()
                .withEsFilterPath("profile.createdAt")
                .withAdditionalFilter("AND profile.createdAt IS NOT NULL AND profile.createdAt = 1658222730748")
                .build();
        DashboardLabelDto labelDto = new DashboardLabelDto.Builder()
                .withDashboardLabelFilter(labelFilterDto)
                .build();
        QueryBuildPayload queryBuildPayload = new QueryBuildPayload(new DDPInstanceDto.Builder().build(), DisplayType.COUNT, labelDto);
        CountAdditionalFilterStrategy countAdditionalFilterStrategy = new CountAdditionalFilterStrategy(queryBuildPayload);
        BoolQueryBuilder actualQuery = countAdditionalFilterStrategy.process();

        BoolQueryBuilder expectedQuery = new BoolQueryBuilder();
        expectedQuery.must(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("profile.createdAt")));
        expectedQuery.must(QueryBuilders.matchQuery("profile.createdAt", 1658222730748L).operator(Operator.AND));

        assertEquals(expectedQuery, actualQuery);
    }

    @Test
    public void processMultipleDifferentAdditionalFilter() {
        DashboardLabelFilterDto labelFilterDto = new DashboardLabelFilterDto.Builder()
                .withEsFilterPath("profile.createdAt")
                .withAdditionalFilter("AND profile.createdAt IS NOT NULL OR address.country = US "
                        + "AND dsm.dateOfBirth IS NOT NULL OR dsm.dateOfMajority IS NOT NULL AND m.faxSent IS NULL")
                .build();
        DashboardLabelDto labelDto = new DashboardLabelDto.Builder()
                .withDashboardLabelFilter(labelFilterDto)
                .build();
        QueryBuildPayload queryBuildPayload = new QueryBuildPayload(new DDPInstanceDto.Builder().build(), DisplayType.COUNT, labelDto);
        CountAdditionalFilterStrategy countAdditionalFilterStrategy = new CountAdditionalFilterStrategy(queryBuildPayload);
        BoolQueryBuilder actualQuery = countAdditionalFilterStrategy.process();

        BoolQueryBuilder expectedQuery = new BoolQueryBuilder();
        expectedQuery.must(
                QueryBuilders.nestedQuery("dsm.medicalRecord",
                        QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery("dsm.medicalRecord.faxSent")), ScoreMode.Avg)
        );
        expectedQuery.must(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("profile.createdAt")));
        expectedQuery.should(QueryBuilders.matchQuery("address.country", "US").operator(Operator.AND));
        expectedQuery.must(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("dsm.dateOfBirth")));
        expectedQuery.should(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("dsm.dateOfMajority")));

        assertEquals(expectedQuery, actualQuery);
    }

    @Test
    public void processMultipleDifferentAdditionalFilterWithActivityQuestionsAnswers() {
        DashboardLabelFilterDto labelFilterDto = new DashboardLabelFilterDto.Builder()
                .withEsFilterPath("profile.createdAt")
                .withAdditionalFilter("AND profile.createdAt IS NOT NULL OR address.country = US "
                        + "AND dsm.dateOfBirth IS NOT NULL OR dsm.dateOfMajority IS NOT NULL AND m.faxSent IS NULL "
                        + "AND MEDICAL_HISTORY.TELANGIECTASIA IS NOT NULL ")
                .build();
        DashboardLabelDto labelDto = new DashboardLabelDto.Builder()
                .withDashboardLabelFilter(labelFilterDto)
                .build();
        QueryBuildPayload queryBuildPayload = new QueryBuildPayload(new DDPInstanceDto.Builder().build(), DisplayType.COUNT, labelDto);
        CountAdditionalFilterStrategy countAdditionalFilterStrategy = new CountAdditionalFilterStrategy(queryBuildPayload);
        BoolQueryBuilder actualQuery = countAdditionalFilterStrategy.process();

        BoolQueryBuilder expectedQuery = new BoolQueryBuilder();
        expectedQuery.must(
                QueryBuilders.nestedQuery("dsm.medicalRecord",
                        QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery("dsm.medicalRecord.faxSent")), ScoreMode.Avg)
        );
        expectedQuery.must(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("profile.createdAt")));
        expectedQuery.must(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("dsm.dateOfBirth")));
        expectedQuery.must(
                QueryBuilders.nestedQuery("activities",
                        QueryBuilders.boolQuery()
                                .must(QueryBuilders.matchQuery("activities.activityCode", "MEDICAL_HISTORY").operator(Operator.AND))
                                .must(QueryBuilders.nestedQuery(
                                        "activities.questionsAnswers",
                                        QueryBuilders.boolQuery()
                                                .must(QueryBuilders.matchQuery("activities.questionsAnswers.stableId", "TELANGIECTASIA")
                                                        .operator(Operator.AND))
                                                .must(QueryBuilders.boolQuery()
                                                        .must(QueryBuilders.existsQuery("activities.questionsAnswers.answer"))),
                                        ScoreMode.Avg
                                )), ScoreMode.Avg)
        );
        expectedQuery.should(QueryBuilders.matchQuery("address.country", "US").operator(Operator.AND));
        expectedQuery.should(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("dsm.dateOfMajority")));

        assertEquals(expectedQuery, actualQuery);
    }

    @Test
    public void processMultipleDifferentAdditionalFilterWithNonActivityQuestionsAnswers() {
        DashboardLabelFilterDto labelFilterDto = new DashboardLabelFilterDto.Builder()
                .withEsFilterPath("profile.createdAt")
                .withAdditionalFilter("AND profile.createdAt IS NOT NULL OR address.country = US "
                        + "AND dsm.dateOfBirth IS NOT NULL OR dsm.dateOfMajority IS NOT NULL AND m.faxSent IS NULL "
                        + "AND PREQUAL.createdAt IS NOT NULL  ")
                .build();
        DashboardLabelDto labelDto = new DashboardLabelDto.Builder()
                .withDashboardLabelFilter(labelFilterDto)
                .build();
        QueryBuildPayload queryBuildPayload = new QueryBuildPayload(new DDPInstanceDto.Builder().build(), DisplayType.COUNT, labelDto);
        CountAdditionalFilterStrategy countAdditionalFilterStrategy = new CountAdditionalFilterStrategy(queryBuildPayload);
        BoolQueryBuilder actualQuery = countAdditionalFilterStrategy.process();

        BoolQueryBuilder expectedQuery = new BoolQueryBuilder();
        expectedQuery.must(
                QueryBuilders.nestedQuery("dsm.medicalRecord",
                        QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery("dsm.medicalRecord.faxSent")), ScoreMode.Avg)
        );
        expectedQuery.must(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("profile.createdAt")));
        expectedQuery.must(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("dsm.dateOfBirth")));
        expectedQuery.must(
                QueryBuilders.nestedQuery("activities",
                        QueryBuilders.boolQuery()
                                .must(QueryBuilders.matchQuery("activities.activityCode", "PREQUAL").operator(Operator.AND))
                                .must(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("activities.createdAt"))),
                        ScoreMode.Avg
                )
        );
        expectedQuery.should(QueryBuilders.matchQuery("address.country", "US").operator(Operator.AND));
        expectedQuery.should(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("dsm.dateOfMajority")));

        assertEquals(expectedQuery, actualQuery);
    }


}
