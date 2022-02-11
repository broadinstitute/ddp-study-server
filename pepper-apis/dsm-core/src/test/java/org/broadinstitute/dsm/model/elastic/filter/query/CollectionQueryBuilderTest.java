package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.regex.Pattern;

import org.apache.lucene.search.join.ScoreMode;
import org.broadinstitute.dsm.model.elastic.filter.FilterParser;
import org.elasticsearch.index.query.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CollectionQueryBuilderTest {

    DsmAbstractQueryBuilder dsmAbstractQueryBuilder;

    @Before
    public void setUp() {
        dsmAbstractQueryBuilder = new DsmAbstractQueryBuilder();
        dsmAbstractQueryBuilder.setParser(new FilterParser());
    }

    @Test
    public void collectionBuild() {

        String filter = "AND m.medicalRecordId = '15' AND m.type = 'PHYSICIAN' OR k.bspCollaboratorSampleId = 'ASCProject_PZ8GJC_SALIVA'";

        dsmAbstractQueryBuilder.setFilter(filter);
        AbstractQueryBuilder actual = dsmAbstractQueryBuilder.build();

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder().must(new NestedQueryBuilder("dsm.medicalRecord", new MatchQueryBuilder("dsm.medicalRecord.medicalRecordId", "15"), ScoreMode.Avg))
                .must(new NestedQueryBuilder("dsm.medicalRecord", new MatchQueryBuilder("dsm.medicalRecord.type", "PHYSICIAN"), ScoreMode.Avg))
                .should(new NestedQueryBuilder("dsm.kitRequestShipping", new MatchQueryBuilder("dsm.kitRequestShipping.bspCollaboratorSampleId", "ASCProject_PZ8GJC_SALIVA"), ScoreMode.Avg));

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void collectionBuild2() {

        String filter = "AND m.medicalRecordId >= '15' AND m.type LIKE 'PHYSICIAN' OR k.bspCollaboratorSampleId = 'ASCProject_PZ8GJC_SALIVA' AND t.returnDate <= '2015-01-01' AND p.participantId IS NOT NULL";

        dsmAbstractQueryBuilder.setFilter(filter);
        AbstractQueryBuilder actual = dsmAbstractQueryBuilder.build();


        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        boolQueryBuilder.must(new ExistsQueryBuilder("dsm.participant.participantId"));

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder().must(new NestedQueryBuilder("dsm.medicalRecord", new RangeQueryBuilder("dsm.medicalRecord.medicalRecordId").gte("15"), ScoreMode.Avg))
                .must(new NestedQueryBuilder("dsm.medicalRecord", new MatchQueryBuilder("dsm.medicalRecord.type", "PHYSICIAN"), ScoreMode.Avg))
                .should(new NestedQueryBuilder("dsm.kitRequestShipping", new MatchQueryBuilder("dsm.kitRequestShipping.bspCollaboratorSampleId", "ASCProject_PZ8GJC_SALIVA"), ScoreMode.Avg))
                .must(new NestedQueryBuilder("dsm.tissue", new RangeQueryBuilder("dsm.tissue.returnDate").lte("2015-01-01"), ScoreMode.Avg))
                .must(boolQueryBuilder);

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void collectionBuildAgeRange() {

        String filter = "AND m.age >= '15' AND m.age <= '30'";

        dsmAbstractQueryBuilder.setFilter(filter);
        AbstractQueryBuilder actual = dsmAbstractQueryBuilder.build();

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder().must(new NestedQueryBuilder("dsm.medicalRecord",
                        new RangeQueryBuilder("dsm.medicalRecord.age").gte("15"), ScoreMode.Avg))
                .must(new NestedQueryBuilder("dsm.medicalRecord", new RangeQueryBuilder("dsm.medicalRecord.age").lte("30"),
                        ScoreMode.Avg));

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void collectionBuildBoolean() {

        String filter = "AND m.followUp LIKE '1'";

        dsmAbstractQueryBuilder.setFilter(filter);
        AbstractQueryBuilder actual = dsmAbstractQueryBuilder.build();

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder().must(new NestedQueryBuilder("dsm.medicalRecord",
                        new MatchQueryBuilder("dsm.medicalRecord.followUp", true), ScoreMode.Avg));

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void multipleOptionsQueryBuilder() {

        String filter = "AND ( oD.request = 'review' OR oD.request = 'no' OR oD.request = 'hold' OR oD.request = 'request' OR oD.request = 'unable To Obtain' OR oD.request = 'sent' OR oD.request = 'received' OR oD.request = 'returned' )";

        dsmAbstractQueryBuilder.setFilter(filter);
        AbstractQueryBuilder actual = dsmAbstractQueryBuilder.build();

        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        boolQueryBuilder.should(new MatchQueryBuilder("dsm.oncHistoryDetail.request", "review"));
        boolQueryBuilder.should(new MatchQueryBuilder("dsm.oncHistoryDetail.request", "no"));
        boolQueryBuilder.should(new MatchQueryBuilder("dsm.oncHistoryDetail.request", "hold"));
        boolQueryBuilder.should(new MatchQueryBuilder("dsm.oncHistoryDetail.request", "request"));
        boolQueryBuilder.should(new MatchQueryBuilder("dsm.oncHistoryDetail.request", "unable To Obtain"));
        boolQueryBuilder.should(new MatchQueryBuilder("dsm.oncHistoryDetail.request", "sent"));
        boolQueryBuilder.should(new MatchQueryBuilder("dsm.oncHistoryDetail.request", "received"));
        boolQueryBuilder.should(new MatchQueryBuilder("dsm.oncHistoryDetail.request", "returned"));

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder().must(new NestedQueryBuilder("dsm.oncHistoryDetail",
                        boolQueryBuilder, ScoreMode.Avg));

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void dateGreaterBuild() {

        String filter = "AND m.received >= STR_TO_DATE('2012-01-01', %yyyy-%MM-%dd) AND m.received <= STR_TO_DATE('2015-01-01', %yyyy-%MM-%dd)";

        dsmAbstractQueryBuilder.setFilter(filter);
        AbstractQueryBuilder actual = dsmAbstractQueryBuilder.build();

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder().must(new NestedQueryBuilder("dsm.medicalRecord",
                        new RangeQueryBuilder("dsm.medicalRecord.received").gte("2012-01-01"), ScoreMode.Avg))
                .must(new NestedQueryBuilder("dsm.medicalRecord", new RangeQueryBuilder("dsm.medicalRecord.received").lte("2015-01-01"),
                        ScoreMode.Avg));

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void dynamicFieldsQueryBuild() {

        String filter = "AND JSON_EXTRACT ( m.additional_values_json , '$.seeingIfBugExists' ) = 'true' AND JSON_EXTRACT ( m.additional_values_json , '$.tryAgain' ) IS NOT NULL";

        dsmAbstractQueryBuilder.setFilter(filter);
        AbstractQueryBuilder actual = dsmAbstractQueryBuilder.build();

        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        boolQueryBuilder.must(new NestedQueryBuilder("dsm.medicalRecord", new ExistsQueryBuilder("dsm.medicalRecord.dynamicFields.tryAgain"), ScoreMode.Avg));

        AbstractQueryBuilder<BoolQueryBuilder> expected = new BoolQueryBuilder().must(new NestedQueryBuilder("dsm.medicalRecord",
                        new MatchQueryBuilder("dsm.medicalRecord.dynamicFields.seeingIfBugExists", true), ScoreMode.Avg)).must(boolQueryBuilder);

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void dsmAliasRegex() {
        Pattern dsmAliasRegex = Pattern.compile("(AND) (m|p|r|t|d|oD|o|JSO|\\()(\\.|\\s|)([a-z]*)");
        String substringToMatch = "AND JSON_EXTRACT ( m.additional_values_json , '$.seeingIfBugExists' )".substring(0, 7);
        Assert.assertTrue(dsmAliasRegex.matcher(substringToMatch).matches());
    }

}