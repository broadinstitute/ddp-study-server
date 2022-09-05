package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.regex.Pattern;

import org.apache.lucene.search.join.ScoreMode;
import org.broadinstitute.dsm.model.elastic.filter.FilterParser;
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

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder("m", filter).build();

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

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder("m", filter).build();


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

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder("m", filter).build();

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder().must(
                        new NestedQueryBuilder("dsm.medicalRecord",
                                new RangeQueryBuilder("dsm.medicalRecord.age").gte("15"), ScoreMode.Avg))
                .must(new NestedQueryBuilder("dsm.medicalRecord",
                        new RangeQueryBuilder("dsm.medicalRecord.age").lte("30"), ScoreMode.Avg));

        Assert.assertEquals(expected, actual);
    }

    private BaseAbstractQueryBuilder getAbstractQueryBuilder(String alias, String filter) {
        BaseAbstractQueryBuilder abstractQueryBuilder = AbstractQueryBuilderFactory.create(alias, filter);
        abstractQueryBuilder.setParser(new FilterParser());
        return abstractQueryBuilder;
    }

    @Test
    public void collectionBuildBoolean() {

        String filter = "AND m.followUp LIKE '1'";

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder("m", filter).build();

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder().must(
                new NestedQueryBuilder("dsm.medicalRecord", new MatchQueryBuilder("dsm.medicalRecord.followUp", true), ScoreMode.Avg));

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void multipleOptionsQueryBuilder() {

        String filter =
                "AND ( oD.request = 'review' OR oD.request = 'no' OR oD.request = 'hold' OR oD.request = 'request' "
                        + "OR oD.request = 'unable To Obtain' OR oD.request = 'sent' OR oD.request = 'received' "
                        + "OR oD.request = 'returned' )";

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder("oD", filter).build();

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

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder("m", filter).build();

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

        AbstractQueryBuilder<?> actual = getAbstractQueryBuilder("m", filter).build();


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

}
