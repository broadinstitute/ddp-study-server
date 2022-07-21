package org.broadinstitute.dsm.model.elastic.export.painless;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.apache.lucene.search.join.ScoreMode;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.elastic.MockFieldTypeExtractor;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.junit.Test;

public class NestedUpsertPainlessFacadeTest {

    @Test
    public void buildQueryBuilder() {
        QueryBuilder queryBuilder = getUpsertFacadePainless().buildQueryBuilder();
        String path = "dsm.kitRequestShipping";
        String fieldName = String.join(DBConstants.ALIAS_DELIMITER, path, "kitLabel");
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        TermQueryBuilder kitLabelTerm = new TermQueryBuilder(fieldName, "KIT_LABEL");
        boolQueryBuilder.must(kitLabelTerm);
        NestedQueryBuilder expected = new NestedQueryBuilder(path, boolQueryBuilder, ScoreMode.Avg);
        assertEquals(expected, queryBuilder);
    }

    private UpsertPainlessFacade getUpsertFacadePainless() {
        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        kitRequestShipping.setKitLabel("KIT_LABEL");
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDto.Builder().build();
        UpsertPainlessFacade painlessFacade =
                new NestedUpsertPainlessFacade(kitRequestShipping, ddpInstanceDto, ESObjectConstants.KIT_LABEL, ESObjectConstants.KIT_LABEL,
                        "KIT_LABEL", new MockFieldTypeExtractor(), new PutToNestedScriptBuilder());
        return painlessFacade;
    }

    @Test
    public void buildQueryBuilderForDocId() {
        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        kitRequestShipping.setKitLabel("KIT_LABEL");
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDto.Builder().build();
        NestedUpsertPainlessFacade painlessFacade =
                new NestedUpsertPainlessFacade(kitRequestShipping, ddpInstanceDto, ESObjectConstants.DSM_KIT_REQUEST_ID,
                        ESObjectConstants.DOC_ID, "GUID6782", new MockFieldTypeExtractor(), new PutToNestedScriptBuilder());
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        TermQueryBuilder termQueryBuilder = new TermQueryBuilder(ESObjectConstants.DOC_ID, "GUID6782");
        boolQueryBuilder.must(termQueryBuilder);
        QueryBuilder finalQuery = painlessFacade.buildFinalQuery(boolQueryBuilder);
        assertEquals(boolQueryBuilder, finalQuery);
    }

    @Test
    public void buildQueryBuilderForRemoveNestedCohortTagId() {
        CohortTag cohortTag = new CohortTag();
        int cohortTagId = 12;
        cohortTag.setCohortTagId(cohortTagId);
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDto.Builder().build();
        NestedUpsertPainlessFacade painlessFacade =
                new NestedUpsertPainlessFacade(cohortTag, ddpInstanceDto, ESObjectConstants.DSM_COHORT_TAG_ID,
                        ESObjectConstants.DSM_COHORT_TAG_ID, cohortTagId, new MockFieldTypeExtractor(),
                        new RemoveFromNestedScriptBuilder());
        QueryBuilder actual = painlessFacade.buildQueryBuilder();

        String nestedPath = String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.COHORT_TAG);
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        TermQueryBuilder cohortTagIdTerm = new TermQueryBuilder(
                String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.COHORT_TAG,
                        ESObjectConstants.DSM_COHORT_TAG_ID), cohortTagId);
        boolQueryBuilder.must(cohortTagIdTerm);
        NestedQueryBuilder expected = new NestedQueryBuilder(nestedPath, boolQueryBuilder, ScoreMode.Avg);
        assertEquals(expected, actual);
    }

    @Test
    public void buildQueryBuilderForAddCohortListToNested() {
        List<String> guids = Arrays.asList("TEST01", "TEST02");
        List<CohortTag> cohortTags = Arrays.asList(
                new CohortTag(10, "testTag", "TEST01", 11),
                new CohortTag(11, "testTag1", "TEST02", 11)
        );
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDto.Builder().build();
        NestedUpsertPainlessFacade painlessFacade =
                new NestedUpsertPainlessFacade(cohortTags, ddpInstanceDto, ESObjectConstants.GUID,
                        String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.PROFILE, ESObjectConstants.GUID),
                        guids, new MockFieldTypeExtractor(), new AddListToNestedByGuidScriptBuilder());
        QueryBuilder actual = painlessFacade.buildQueryBuilder();

        BoolQueryBuilder expected = new BoolQueryBuilder();
        TermsQueryBuilder cohortTagIdTerms = new TermsQueryBuilder(
                String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.PROFILE, ESObjectConstants.GUID), guids);
        expected.must(cohortTagIdTerms);

        assertEquals(expected, actual);

    }
}
