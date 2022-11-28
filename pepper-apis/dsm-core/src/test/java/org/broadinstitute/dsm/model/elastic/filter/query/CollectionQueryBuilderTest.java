package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.regex.Pattern;

import org.apache.lucene.search.join.ScoreMode;
import org.broadinstitute.dsm.model.elastic.filter.FilterParser;
import org.broadinstitute.dsm.model.filter.participant.BaseFilterParticipantList;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.junit.Assert;
import org.junit.Test;

public class CollectionQueryBuilderTest {

    @Test
    public void collectionBuild() {

        String filter = "AND m.medicalRecordId = '15' AND m.type = 'PHYSICIAN' OR k.bspCollaboratorSampleId = 'ASCProject_PZ8GJC_SALIVA'";

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder(filter).build();

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder().must(
                        new NestedQueryBuilder("dsm.medicalRecord", new MatchQueryBuilder("dsm.medicalRecord.medicalRecordId", "15")
                                .operator(Operator.AND),
                                ScoreMode.Avg))
                .must(new NestedQueryBuilder("dsm.medicalRecord", new MatchQueryBuilder("dsm.medicalRecord.type", "PHYSICIAN")
                        .operator(Operator.AND),
                        ScoreMode.Avg)).should(new NestedQueryBuilder("dsm.kitRequestShipping",
                        new MatchQueryBuilder("dsm.kitRequestShipping.bspCollaboratorSampleId", "ASCProject_PZ8GJC_SALIVA")
                                .operator(Operator.AND),
                        ScoreMode.Avg));

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void collectionBuild2() {

        String filter =
                "AND m.medicalRecordId >= '15' AND m.type LIKE 'PHYSICIAN' OR k.bspCollaboratorSampleId = 'ASCProject_PZ8GJC_SALIVA' "
                        + "AND t.returnDate <= '2015-01-01' AND p.participantId IS NOT NULL";

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder(filter).build();


        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        boolQueryBuilder.must(new ExistsQueryBuilder("dsm.participant.participantId"));

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder().must(
                        new NestedQueryBuilder("dsm.medicalRecord", new RangeQueryBuilder("dsm.medicalRecord.medicalRecordId").gte("15"),
                                ScoreMode.Avg))
                .must(new NestedQueryBuilder("dsm.medicalRecord", new MatchQueryBuilder("dsm.medicalRecord.type", "PHYSICIAN"),
                        ScoreMode.Avg)).should(new NestedQueryBuilder("dsm.kitRequestShipping",
                        new MatchQueryBuilder("dsm.kitRequestShipping.bspCollaboratorSampleId", "ASCProject_PZ8GJC_SALIVA")
                                .operator(Operator.AND), ScoreMode.Avg))
                .must(new NestedQueryBuilder("dsm.tissue", new RangeQueryBuilder("dsm.tissue.returnDate").lte("2015-01-01"), ScoreMode.Avg))
                .must(boolQueryBuilder);

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void collectionBuildAgeRange() {

        String filter = "AND m.age >= '15' AND m.age <= '30'";

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder(filter).build();

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder().must(
                        new NestedQueryBuilder("dsm.medicalRecord",
                                new RangeQueryBuilder("dsm.medicalRecord.age").gte("15"), ScoreMode.Avg))
                .must(new NestedQueryBuilder("dsm.medicalRecord",
                        new RangeQueryBuilder("dsm.medicalRecord.age").lte("30"), ScoreMode.Avg));

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void collectionBuildDateOfMajorityRange() {

        String filter = "  AND dsm.dateOfMajority >= '2022-10-26'";

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder(filter).build();

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder()
                .must(new RangeQueryBuilder("dsm.dateOfMajority").gte("2022-10-26"));

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void collectionBuildMixedSources() {

        String filter = " AND c.cohort_tag_name = '7' AND dsm.dateOfMajority  >= '2022-10-26'";

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder(filter).build();

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder()
                .must(new NestedQueryBuilder("dsm.cohortTag",
                        new MatchQueryBuilder("dsm.cohortTag.cohortTagName", "7").operator(Operator.AND), ScoreMode.Avg))
                .must(new RangeQueryBuilder("dsm.dateOfMajority").gte("2022-10-26"));

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void cohortNameValue() {

        String filter = " AND c.cohort_tag_name = '7'";

        AbstractQueryBuilder<?> actual = BaseFilterParticipantList.createMixedSourceBaseAbstractQueryBuilder(filter);

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder()
                .must(new NestedQueryBuilder("dsm.cohortTag",
                        new MatchQueryBuilder("dsm.cohortTag.cohortTagName", "7").operator(Operator.AND), ScoreMode.Avg));

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void scanDateRangeValue() {
        String filter = " AND k.scan_date  >= 1664928000000 AND k.scan_date  <= 1665014399999";

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder(filter).build();

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder()
                .must(new NestedQueryBuilder("dsm.kitRequestShipping",
                        new RangeQueryBuilder("dsm.kitRequestShipping.scanDate").gte(1664928000000L), ScoreMode.Avg))
                .must(new NestedQueryBuilder("dsm.kitRequestShipping",
                        new RangeQueryBuilder("dsm.kitRequestShipping.scanDate").lte(1665014399999L), ScoreMode.Avg));

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void receivedDateValue() {
        String filter = " AND DATE(FROM_UNIXTIME(k.receive_date/1000)) = DATE(FROM_UNIXTIME(1664928000))";

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder(filter).build();

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder()
                .must(new NestedQueryBuilder("dsm.kitRequestShipping",
                        new MatchQueryBuilder("dsm.kitRequestShipping.receiveDate", "2022-10-05").operator(Operator.AND), ScoreMode.Avg));

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void collectionBuildRegistrationRange() {
        String filter = "AND profile.createdAt >= '01/01/2020' AND profile.createdAt <= '01/01/2022'";

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder(filter).build();

        BoolQueryBuilder expected = new BoolQueryBuilder();
        expected.must(new RangeQueryBuilder("profile.createdAt").gte("01/01/2020"));
        expected.must(new RangeQueryBuilder("profile.createdAt").lte("01/01/2022"));

        Assert.assertEquals(expected, actual);
    }

    private BaseAbstractQueryBuilder getAbstractQueryBuilder(String filter) {
        BaseAbstractQueryBuilder abstractQueryBuilder = AbstractQueryBuilderFactory.create(filter);
        abstractQueryBuilder.setParser(new FilterParser());
        return abstractQueryBuilder;
    }

    @Test
    public void collectionBuildBoolean() {

        String filter = "AND m.followUp LIKE '1'";

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder(filter).build();

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder().must(
                new NestedQueryBuilder("dsm.medicalRecord", new MatchQueryBuilder("dsm.medicalRecord.followUp", true), ScoreMode.Avg));

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void smIdNotEmpty() {
        String filter = " AND sm.sm_id_value IS NOT NULL ";

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder(filter).build();
        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder().must(new NestedQueryBuilder("dsm.smId",
                            new BoolQueryBuilder().must(new ExistsQueryBuilder("dsm.smId.smIdValue")), ScoreMode.Avg));
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void multipleOptionsQueryBuilder() {

        String filter =
                "AND ( oD.request = 'review' OR oD.request = 'no' OR oD.request = 'hold' OR oD.request = 'request' "
                        + "OR oD.request = 'unable To Obtain' OR oD.request = 'sent' OR oD.request = 'received' "
                        + "OR oD.request = 'returned' )";

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder(filter).build();

        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        boolQueryBuilder.should(new MatchQueryBuilder("dsm.oncHistoryDetail.request", "review"));
        boolQueryBuilder.should(new MatchQueryBuilder("dsm.oncHistoryDetail.request", "no"));
        boolQueryBuilder.should(new MatchQueryBuilder("dsm.oncHistoryDetail.request", "hold"));
        boolQueryBuilder.should(new MatchQueryBuilder("dsm.oncHistoryDetail.request", "request"));
        boolQueryBuilder.should(new MatchQueryBuilder("dsm.oncHistoryDetail.request", "unable To Obtain"));
        boolQueryBuilder.should(new MatchQueryBuilder("dsm.oncHistoryDetail.request", "sent"));
        boolQueryBuilder.should(new MatchQueryBuilder("dsm.oncHistoryDetail.request", "received"));
        boolQueryBuilder.should(new MatchQueryBuilder("dsm.oncHistoryDetail.request", "returned"));

        AbstractQueryBuilder<BoolQueryBuilder> expected =
                new BoolQueryBuilder().must(new NestedQueryBuilder("dsm.oncHistoryDetail", boolQueryBuilder, ScoreMode.Avg));

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void dateGreaterBuild() {

        String filter =
                "AND m.received >= STR_TO_DATE('2012-01-01', %yyyy-%MM-%dd) AND m.received <= STR_TO_DATE('2015-01-01', %yyyy-%MM-%dd)";

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder(filter).build();

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder().must(
                        new NestedQueryBuilder("dsm.medicalRecord", new RangeQueryBuilder("dsm.medicalRecord.received").gte("2012-01-01"),
                                ScoreMode.Avg))
                .must(new NestedQueryBuilder("dsm.medicalRecord", new RangeQueryBuilder("dsm.medicalRecord.received").lte("2015-01-01"),
                        ScoreMode.Avg));

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void dynamicFieldsQueryBuild() {

        String filter =
                "AND JSON_EXTRACT ( m.additional_values_json , '$.seeingIfBugExists' ) = 'true' "
                        + "AND JSON_EXTRACT ( m.additional_values_json , '$.tryAgain' ) IS NOT NULL";

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder(filter).build();


        NestedQueryBuilder nestedQueryBuilder = new NestedQueryBuilder("dsm.medicalRecord",
                new BoolQueryBuilder().must(new ExistsQueryBuilder("dsm.medicalRecord.dynamicFields.tryAgain")),
                ScoreMode.Avg);

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder().must(new NestedQueryBuilder("dsm.medicalRecord",
                new MatchQueryBuilder("dsm.medicalRecord.dynamicFields.seeingIfBugExists", true).operator(Operator.AND),
                ScoreMode.Avg)).must(nestedQueryBuilder);

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void dsmAliasRegex() {
        Pattern dsmAliasRegex = Pattern.compile("(AND) (m|p|r|t|d|oD|o|JSO|\\()(\\.|\\s|)([a-z]*)");
        String substringToMatch = "AND JSON_EXTRACT ( m.additional_values_json , '$.seeingIfBugExists' )".substring(0, 7);
        Assert.assertTrue(dsmAliasRegex.matcher(substringToMatch).matches());
    }

    @Test
    public void parseQueryWithNumberInString() {
        String filter = " AND c.cohort_tag_name = 'Oct 7 2022'";

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder(filter).build();

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder().must(
                        new NestedQueryBuilder("dsm.cohortTag",
                                new MatchQueryBuilder("dsm.cohortTag.cohortTagName", "Oct 7 2022")
                                .operator(Operator.AND), ScoreMode.Avg));

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void parseQueryWithNumber() {
        String filter = " AND c.cohort_tag_name = '7'";

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder(filter).build();

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder().must(
                        new NestedQueryBuilder("dsm.cohortTag",
                                new MatchQueryBuilder("dsm.cohortTag.cohortTagName", "7")
                                .operator(Operator.AND), ScoreMode.Avg));

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void parseLikeQueryWithNumberInString() {
        String filter = " AND c.cohort_tag_name LIKE '%Oct 7 2022%'";

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder(filter).build();

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder().must(
                new NestedQueryBuilder("dsm.cohortTag",
                        new MatchQueryBuilder("dsm.cohortTag.cohortTagName", "Oct 7 2022")
                                .operator(Operator.OR), ScoreMode.Avg));

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void parseLikeQueryWithDoubleSpacesInString() {
        String filter = " AND c.cohort_tag_name LIKE '%Oct 7  2022%'";

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder(filter).build();

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder().must(
                new NestedQueryBuilder("dsm.cohortTag",
                        new MatchQueryBuilder("dsm.cohortTag.cohortTagName", "Oct 7 2022")
                                .operator(Operator.OR), ScoreMode.Avg));

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void parseLikeQueryWithMultipleSpacesInString() {
        String filter = " AND c.cohort_tag_name LIKE '%Oct 7     2022%'";

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder(filter).build();

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder().must(
                new NestedQueryBuilder("dsm.cohortTag",
                        new MatchQueryBuilder("dsm.cohortTag.cohortTagName", "Oct 7 2022")
                                .operator(Operator.OR), ScoreMode.Avg));

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void parseEqualsQueryWithDoubleSpacesInString() {
        String filter = " AND c.cohort_tag_name = 'Oct 7  2022'";

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder(filter).build();

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder().must(
                new NestedQueryBuilder("dsm.cohortTag",
                        new MatchQueryBuilder("dsm.cohortTag.cohortTagName", "Oct 7 2022")
                                .operator(Operator.AND), ScoreMode.Avg));

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void parseEqualsQueryWithMultipleSpacesInString() {
        String filter = " AND c.cohort_tag_name = 'Oct 7     2022'";

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder(filter).build();

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder().must(
                new NestedQueryBuilder("dsm.cohortTag",
                        new MatchQueryBuilder("dsm.cohortTag.cohortTagName", "Oct 7 2022")
                                .operator(Operator.AND), ScoreMode.Avg));

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void parseEqualsQueryForDynamicFormField() {
        String filter = " AND participantData.ACCEPTANCE_STATUS = 'MORE_INFO_NEEDED' ";

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder(filter).build();

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder().must(
                new NestedQueryBuilder("dsm.participantData",
                        new MatchQueryBuilder("dsm.participantData.dynamicFields.acceptanceStatus", "MORE_INFO_NEEDED")
                                .operator(Operator.AND), ScoreMode.Avg));

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void parseEqualsQueryForDynamicFormText() {
        String filter = "  AND participantData.IMPORTANT_NOTES = 'Test' ";

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder(filter).build();

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder().must(
                new NestedQueryBuilder("dsm.participantData",
                        new MatchQueryBuilder("dsm.participantData.dynamicFields.importantNotes", "Test")
                                .operator(Operator.AND), ScoreMode.Avg));

        Assert.assertEquals(expected, actual);
    }
}
