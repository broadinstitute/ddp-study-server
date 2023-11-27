package org.broadinstitute.dsm.model.dashboard;

import static org.junit.Assert.assertEquals;

import org.apache.lucene.search.join.ScoreMode;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelDto;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelFilterDto;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.junit.Test;

public class CountAdditionalFilterStrategyTest {


    @Test
    public void processNonNestedSingleAdditionalFilter() {
        DashboardLabelFilterDto labelFilterDto = new DashboardLabelFilterDto.Builder().withEsFilterPath("profile.createdAt")
                .withAdditionalFilter("AND profile.createdAt IS NOT NULL").build();
        DashboardLabelDto labelDto = new DashboardLabelDto.Builder().withDashboardLabelFilter(labelFilterDto).build();
        QueryBuildPayload queryBuildPayload = new QueryBuildPayload(new DDPInstanceDto.Builder().build(), DisplayType.COUNT, labelDto);
        CountAdditionalFilterStrategy countAdditionalFilterStrategy = new CountAdditionalFilterStrategy(queryBuildPayload);
        BoolQueryBuilder actualQuery = (BoolQueryBuilder) countAdditionalFilterStrategy.process().must().get(0);

        BoolQueryBuilder expectedQuery = new BoolQueryBuilder();
        expectedQuery.must(QueryBuilders.existsQuery("profile.createdAt"));

        assertEquals(expectedQuery, actualQuery);
    }

    @Test
    public void processNonNestedMultipleAdditionalFilter() {
        DashboardLabelFilterDto labelFilterDto = new DashboardLabelFilterDto.Builder().withEsFilterPath("profile.createdAt")
                .withAdditionalFilter("AND profile.createdAt IS NOT NULL AND profile.createdAt = 1658222730748").build();
        DashboardLabelDto labelDto = new DashboardLabelDto.Builder().withDashboardLabelFilter(labelFilterDto).build();
        QueryBuildPayload queryBuildPayload = new QueryBuildPayload(new DDPInstanceDto.Builder().build(), DisplayType.COUNT, labelDto);
        CountAdditionalFilterStrategy countAdditionalFilterStrategy = new CountAdditionalFilterStrategy(queryBuildPayload);
        BoolQueryBuilder actualQuery = countAdditionalFilterStrategy.process();

        BoolQueryBuilder expectedQuery = new BoolQueryBuilder();
        expectedQuery.must(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("profile.createdAt")));
        expectedQuery.must(QueryBuilders.matchQuery("profile.createdAt", 1658222730748L).operator(Operator.AND));

        assertEquals(expectedQuery, actualQuery);
    }

    @Test
    public void processNonNestedMultipleAdditionalFilterRange() {
        DashboardLabelFilterDto labelFilterDto = new DashboardLabelFilterDto.Builder().withEsFilterPath("profile.createdAt")
                .withAdditionalFilter("AND profile.createdAt IS NOT NULL AND profile.createdAt >= '01/01/2020'").build();
        DashboardLabelDto labelDto = new DashboardLabelDto.Builder().withDashboardLabelFilter(labelFilterDto).build();
        QueryBuildPayload queryBuildPayload = new QueryBuildPayload(new DDPInstanceDto.Builder().build(), DisplayType.COUNT, labelDto);
        CountAdditionalFilterStrategy countAdditionalFilterStrategy = new CountAdditionalFilterStrategy(queryBuildPayload);
        BoolQueryBuilder actualQuery = countAdditionalFilterStrategy.process();

        BoolQueryBuilder expectedQuery = new BoolQueryBuilder();
        expectedQuery.must(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("profile.createdAt")));
        expectedQuery.must(new RangeQueryBuilder("profile.createdAt").gte("01/01/2020"));

        assertEquals(expectedQuery, actualQuery);
    }

    @Test
    public void processMultipleDifferentAdditionalFilterWithNonActivityQuestionsAnswersRange() {
        DashboardLabelFilterDto labelFilterDto = new DashboardLabelFilterDto.Builder().withEsFilterPath("profile.createdAt")
                .withAdditionalFilter("AND profile.createdAt IS NOT NULL AND PREQUAL.completedAt IS NOT NULL  "
                        + "AND PREQUAL.completedAt >=  '01/01/2020'  AND PREQUAL.completedAt <= '01/01/2022'").build();
        DashboardLabelDto labelDto = new DashboardLabelDto.Builder().withDashboardLabelFilter(labelFilterDto).build();
        QueryBuildPayload queryBuildPayload = new QueryBuildPayload(new DDPInstanceDto.Builder().build(), DisplayType.COUNT, labelDto);
        CountAdditionalFilterStrategy countAdditionalFilterStrategy = new CountAdditionalFilterStrategy(queryBuildPayload);
        BoolQueryBuilder actualQuery = countAdditionalFilterStrategy.process();

        BoolQueryBuilder expectedQuery = new BoolQueryBuilder();
        expectedQuery.must(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("profile.createdAt")));
        expectedQuery.must(QueryBuilders.nestedQuery("activities",
                QueryBuilders.boolQuery().must(QueryBuilders.matchQuery("activities.activityCode", "PREQUAL").operator(Operator.AND))
                        .must(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("activities.completedAt"))), ScoreMode.Avg));
        expectedQuery.must(QueryBuilders.nestedQuery("activities",
                QueryBuilders.boolQuery().must(QueryBuilders.matchQuery("activities.activityCode", "PREQUAL").operator(Operator.AND))
                        .must(new RangeQueryBuilder("activities.completedAt").gte("01/01/2020")), ScoreMode.Avg));
        expectedQuery.must(QueryBuilders.nestedQuery("activities",
                QueryBuilders.boolQuery().must(QueryBuilders.matchQuery("activities.activityCode", "PREQUAL").operator(Operator.AND))
                        .must(new RangeQueryBuilder("activities.completedAt").lte("01/01/2022")), ScoreMode.Avg));

        assertEquals(expectedQuery, actualQuery);
    }

    @Test
    public void processStatusAndCreatedRange() {
        DashboardLabelFilterDto labelFilterDto = new DashboardLabelFilterDto.Builder().withEsFilterPath("status")
                .withAdditionalFilter("AND ( data.status = 'ENROLLED' ) "
                        + " AND profile.createdAt  >= '01/01/2020' AND profile.createdAt  <= '01/01/2022'").build();
        DashboardLabelDto labelDto = new DashboardLabelDto.Builder().withDashboardLabelFilter(labelFilterDto).build();
        QueryBuildPayload queryBuildPayload = new QueryBuildPayload(new DDPInstanceDto.Builder().build(), DisplayType.COUNT, labelDto);
        CountAdditionalFilterStrategy countAdditionalFilterStrategy = new CountAdditionalFilterStrategy(queryBuildPayload);
        BoolQueryBuilder actualQuery = countAdditionalFilterStrategy.process();

        BoolQueryBuilder expectedQuery = new BoolQueryBuilder();
        expectedQuery.must(new BoolQueryBuilder().should(new MatchQueryBuilder("data.status", "ENROLLED").operator(Operator.OR)));
        expectedQuery.must(new RangeQueryBuilder("profile.createdAt").gte("01/01/2020"));
        expectedQuery.must(new RangeQueryBuilder("profile.createdAt").lte("01/01/2022"));

        assertEquals(expectedQuery, actualQuery);
    }

    @Test
    public void processMultipleDifferentAdditionalFilter() {
        DashboardLabelFilterDto labelFilterDto = new DashboardLabelFilterDto.Builder().withEsFilterPath("profile.createdAt")
                .withAdditionalFilter("AND profile.createdAt IS NOT NULL OR address.country = US "
                        + "AND dsm.dateOfBirth IS NOT NULL OR dsm.dateOfMajority IS NOT NULL AND m.faxSent IS NULL").build();
        DashboardLabelDto labelDto = new DashboardLabelDto.Builder().withDashboardLabelFilter(labelFilterDto).build();
        QueryBuildPayload queryBuildPayload = new QueryBuildPayload(new DDPInstanceDto.Builder().build(), DisplayType.COUNT, labelDto);
        CountAdditionalFilterStrategy countAdditionalFilterStrategy = new CountAdditionalFilterStrategy(queryBuildPayload);
        BoolQueryBuilder actualQuery = countAdditionalFilterStrategy.process();

        BoolQueryBuilder expectedQuery = new BoolQueryBuilder();
        expectedQuery.must(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("profile.createdAt")));
        expectedQuery.must(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("dsm.dateOfBirth")));
        expectedQuery.must(QueryBuilders.nestedQuery("dsm.medicalRecord",
                QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery("dsm.medicalRecord.faxSent")), ScoreMode.Avg));
        expectedQuery.should(QueryBuilders.matchQuery("address.country", "US").operator(Operator.AND));
        expectedQuery.should(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("dsm.dateOfMajority")));

        assertEquals(expectedQuery, actualQuery);
    }

    @Test
    public void processMultipleDifferentAdditionalFilterWithActivityQuestionsAnswers() {
        DashboardLabelFilterDto labelFilterDto = new DashboardLabelFilterDto.Builder().withEsFilterPath("profile.createdAt")
                .withAdditionalFilter("AND profile.createdAt IS NOT NULL OR address.country = US "
                        + "AND dsm.dateOfBirth IS NOT NULL OR dsm.dateOfMajority IS NOT NULL AND m.faxSent IS NULL "
                        + "AND MEDICAL_HISTORY.TELANGIECTASIA IS NOT NULL ").build();
        DashboardLabelDto labelDto = new DashboardLabelDto.Builder().withDashboardLabelFilter(labelFilterDto).build();
        QueryBuildPayload queryBuildPayload = new QueryBuildPayload(new DDPInstanceDto.Builder().build(), DisplayType.COUNT, labelDto);
        CountAdditionalFilterStrategy countAdditionalFilterStrategy = new CountAdditionalFilterStrategy(queryBuildPayload);
        BoolQueryBuilder actualQuery = countAdditionalFilterStrategy.process();

        BoolQueryBuilder expectedQuery = new BoolQueryBuilder();
        expectedQuery.must(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("profile.createdAt")));
        expectedQuery.must(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("dsm.dateOfBirth")));
        expectedQuery.must(QueryBuilders.nestedQuery("dsm.medicalRecord",
                QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery("dsm.medicalRecord.faxSent")), ScoreMode.Avg));
        expectedQuery.must(QueryBuilders.nestedQuery("activities", QueryBuilders.boolQuery()
                .must(QueryBuilders.matchQuery("activities.activityCode", "MEDICAL_HISTORY").operator(Operator.AND))
                .must(QueryBuilders.nestedQuery("activities.questionsAnswers", QueryBuilders.boolQuery()
                                .must(QueryBuilders.matchQuery("activities.questionsAnswers.stableId", "TELANGIECTASIA").operator(Operator.AND))
                                .must(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("activities.questionsAnswers.answer"))),
                        ScoreMode.Avg)), ScoreMode.Avg));
        expectedQuery.should(QueryBuilders.matchQuery("address.country", "US").operator(Operator.AND));
        expectedQuery.should(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("dsm.dateOfMajority")));

        assertEquals(expectedQuery, actualQuery);
    }

    @Test
    public void processMultipleDifferentAdditionalFilterWithNonActivityQuestionsAnswers() {
        DashboardLabelFilterDto labelFilterDto = new DashboardLabelFilterDto.Builder().withEsFilterPath("profile.createdAt")
                .withAdditionalFilter("AND profile.createdAt IS NOT NULL OR address.country = US "
                        + "AND dsm.dateOfBirth IS NOT NULL OR dsm.dateOfMajority IS NOT NULL AND m.faxSent IS NULL "
                        + "AND PREQUAL.createdAt IS NOT NULL  ").build();
        DashboardLabelDto labelDto = new DashboardLabelDto.Builder().withDashboardLabelFilter(labelFilterDto).build();
        QueryBuildPayload queryBuildPayload = new QueryBuildPayload(new DDPInstanceDto.Builder().build(), DisplayType.COUNT, labelDto);
        CountAdditionalFilterStrategy countAdditionalFilterStrategy = new CountAdditionalFilterStrategy(queryBuildPayload);
        BoolQueryBuilder actualQuery = countAdditionalFilterStrategy.process();

        BoolQueryBuilder expectedQuery = new BoolQueryBuilder();
        expectedQuery.must(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("profile.createdAt")));
        expectedQuery.must(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("dsm.dateOfBirth")));
        expectedQuery.must(QueryBuilders.nestedQuery("dsm.medicalRecord",
                QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery("dsm.medicalRecord.faxSent")), ScoreMode.Avg));
        expectedQuery.must(QueryBuilders.nestedQuery("activities",
                QueryBuilders.boolQuery().must(QueryBuilders.matchQuery("activities.activityCode", "PREQUAL").operator(Operator.AND))
                        .must(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("activities.createdAt"))), ScoreMode.Avg));
        expectedQuery.should(QueryBuilders.matchQuery("address.country", "US").operator(Operator.AND));
        expectedQuery.should(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("dsm.dateOfMajority")));

        assertEquals(expectedQuery, actualQuery);
    }

    @Test
    public void processMultipleAdditionalFilterWithParenthesis() {
        DashboardLabelFilterDto labelFilterDto = new DashboardLabelFilterDto.Builder().withEsFilterPath("profile.createdAt")
                .withAdditionalFilter("AND profile.createdAt IS NOT NULL "
                        + "AND ( t.tissueType = 'review' OR t.tissueType = 'no' OR t.tissueType = 'bla' ) ").build();
        DashboardLabelDto labelDto = new DashboardLabelDto.Builder().withDashboardLabelFilter(labelFilterDto).build();
        QueryBuildPayload queryBuildPayload = new QueryBuildPayload(new DDPInstanceDto.Builder().build(), DisplayType.COUNT, labelDto);
        CountAdditionalFilterStrategy countAdditionalFilterStrategy = new CountAdditionalFilterStrategy(queryBuildPayload);
        BoolQueryBuilder actualQuery = countAdditionalFilterStrategy.process();

        BoolQueryBuilder expectedQuery = new BoolQueryBuilder();
        expectedQuery.must(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("profile.createdAt")));
        expectedQuery.must(QueryBuilders.nestedQuery("dsm.tissue",
                QueryBuilders.boolQuery().should(QueryBuilders.matchQuery("dsm.tissue.tissueType", "review"))
                        .should(QueryBuilders.matchQuery("dsm.tissue.tissueType", "no"))
                        .should(QueryBuilders.matchQuery("dsm.tissue.tissueType", "bla")), ScoreMode.Avg));

        assertEquals(expectedQuery, actualQuery);
    }

    @Test
    public void processMultipleAdditionalFilterWithParenthesisAndEdgeCases() {
        DashboardLabelFilterDto labelFilterDto = new DashboardLabelFilterDto.Builder().withEsFilterPath("profile.createdAt")
                .withAdditionalFilter("AND profile.createdAt IS NOT NULL "
                        + "AND ( t.tissueType = 'review' OR t.tissueType = 'no' OR t.tissueType = 'bla' ) "
                        + "OR JSON_EXTRACT ( m.additional_values_json , '$.seeingIfBugExists' ) = true"
                        + "OR STR_TO_DATE(m.fax_sent,'%Y-%m-%d') = STR_TO_DATE('2021-12-17','%Y-%m-%d') "
                        + "AND JSON_CONTAINS ( k.test_result , JSON_OBJECT ( 'result' , 'result' ) ) ").build();
        DashboardLabelDto labelDto = new DashboardLabelDto.Builder().withDashboardLabelFilter(labelFilterDto).build();
        QueryBuildPayload queryBuildPayload = new QueryBuildPayload(new DDPInstanceDto.Builder().build(), DisplayType.COUNT, labelDto);
        CountAdditionalFilterStrategy countAdditionalFilterStrategy = new CountAdditionalFilterStrategy(queryBuildPayload);
        BoolQueryBuilder actualQuery = countAdditionalFilterStrategy.process();

        BoolQueryBuilder expectedQuery = new BoolQueryBuilder();
        expectedQuery.must(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery("profile.createdAt")));
        expectedQuery.must(QueryBuilders.nestedQuery("dsm.tissue",
                QueryBuilders.boolQuery().should(QueryBuilders.matchQuery("dsm.tissue.tissueType", "review"))
                        .should(QueryBuilders.matchQuery("dsm.tissue.tissueType", "no"))
                        .should(QueryBuilders.matchQuery("dsm.tissue.tissueType", "bla")), ScoreMode.Avg));
        expectedQuery.must(QueryBuilders.nestedQuery("dsm.kitRequestShipping",
                QueryBuilders.matchQuery("dsm.kitRequestShipping.testResult.result", "result").operator(Operator.AND), ScoreMode.Avg));
        expectedQuery.should(QueryBuilders.nestedQuery("dsm.medicalRecord",
                QueryBuilders.matchQuery("dsm.medicalRecord.dynamicFields.seeingIfBugExists", true).operator(Operator.AND), ScoreMode.Avg));
        expectedQuery.should(QueryBuilders.nestedQuery("dsm.medicalRecord",
                QueryBuilders.matchQuery("dsm.medicalRecord.faxSent", "2021-12-17").operator(Operator.AND), ScoreMode.Avg));

        assertEquals(expectedQuery, actualQuery);
    }

}
