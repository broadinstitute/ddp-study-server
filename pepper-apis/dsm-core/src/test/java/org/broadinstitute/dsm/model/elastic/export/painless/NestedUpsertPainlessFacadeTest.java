package org.broadinstitute.dsm.model.elastic.export.painless;

import static org.junit.Assert.*;

import org.apache.lucene.search.join.ScoreMode;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.MockFieldTypeExtractor;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.junit.Before;
import org.junit.Test;

public class NestedUpsertPainlessFacadeTest {

    @Test
    public void buildScriptBuilder() {
        assertTrue(getUpsertFacadePainless().buildScriptBuilder() instanceof NestedScriptBuilder);
    }

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
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDto.Builder()
                .build();
        UpsertPainlessFacade painlessFacade = new NestedUpsertPainlessFacade(kitRequestShipping, ddpInstanceDto, ESObjectConstants.KIT_LABEL,
                ESObjectConstants.KIT_LABEL, "KIT_LABEL", new MockFieldTypeExtractor());
        return painlessFacade;
    }

    @Test
    public void buildQueryBuilderForDocId() {
        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        kitRequestShipping.setKitLabel("KIT_LABEL");
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDto.Builder().build();
        NestedUpsertPainlessFacade painlessFacade = new NestedUpsertPainlessFacade(
                kitRequestShipping, ddpInstanceDto, ESObjectConstants.DSM_KIT_REQUEST_ID, ESObjectConstants.DOC_ID, "GUID6782",
                new MockFieldTypeExtractor()
        );
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        TermQueryBuilder termQueryBuilder = new TermQueryBuilder(ESObjectConstants.DOC_ID, "GUID6782");
        boolQueryBuilder.must(termQueryBuilder);
        QueryBuilder finalQuery = painlessFacade.buildFinalQuery(boolQueryBuilder);
        assertEquals(boolQueryBuilder, finalQuery);
    }
}
