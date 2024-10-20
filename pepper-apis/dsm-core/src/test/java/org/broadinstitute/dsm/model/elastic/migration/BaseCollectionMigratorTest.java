package org.broadinstitute.dsm.model.elastic.migration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.Tissue;
import org.junit.Assert;
import org.junit.Test;

public class BaseCollectionMigratorTest {

    @Test
    public void transformObject() {
        BaseCollectionMigrator baseCollectionMigrator = new MockCollectionMigrator();
        baseCollectionMigrator.transformObject(mockOncHistoryDetail());
        Map<String, Object> objectMap = baseCollectionMigrator.transformedList.get(0);
        Object primaryId = objectMap.get("oncHistoryDetailId");
        Assert.assertEquals(23, primaryId);

        baseCollectionMigrator.transformObject(mockTissues());
        Map<String, Object> stringObjectMap = baseCollectionMigrator.transformedList.get(0);
        Assert.assertEquals(11, stringObjectMap.get("tissueId"));
        Assert.assertEquals("notes", stringObjectMap.get("notes"));
    }

    private List mockOncHistoryDetail() {
        OncHistoryDetail oncHistoryDetail =
                new OncHistoryDetail(23, 0, null, null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, mockTissues(), null, null, false, null, null, 0);
        return Collections.singletonList(oncHistoryDetail);
    }

    private List<Tissue> mockTissues() {
        List<Tissue> fieldValue = new ArrayList<>(List.of(new Tissue(11, 22,
                "notes", null, null, "awdwadawdawdawd", null, null, null, null, null, null,
                null, null, "Awdawd", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null), new Tissue(555, null,
                null, null, null, null, null, null, null, "awdawd", null, null,
                null, null, "awdawddwa", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null)));
        return fieldValue;
    }
}
