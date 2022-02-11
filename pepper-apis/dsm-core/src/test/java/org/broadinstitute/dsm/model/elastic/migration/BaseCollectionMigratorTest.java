package org.broadinstitute.dsm.model.elastic.migration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.Tissue;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.Assert;
import org.junit.Test;

public class BaseCollectionMigratorTest {

    @Test
    public void transformObject() {
        BaseCollectionMigrator baseCollectionMigrator = new MockBaseCollectionMigrator("index", "realm", "object");
        baseCollectionMigrator.transformObject(mockOncHistoryDetail());
        Map<String, Object> objectMap = baseCollectionMigrator.transformedList.get(0);
        Object primaryId = objectMap.get("oncHistoryDetailId");
        Assert.assertEquals(23L, primaryId);

        baseCollectionMigrator.transformObject(mockTissues());
        Map<String, Object> stringObjectMap = baseCollectionMigrator.transformedList.get(0);
        Assert.assertEquals(11L, stringObjectMap.get("tissueId"));
        Assert.assertEquals("notes", stringObjectMap.get("notes"));
    }

    private List mockOncHistoryDetail() {
        OncHistoryDetail oncHistoryDetail =
                new OncHistoryDetail(23, 0, null, null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, mockTissues(), null, null, false, null, null);
        return Collections.singletonList(oncHistoryDetail);
    }

    private List<Tissue> mockTissues() {
        List<Tissue> fieldValue = new ArrayList<>(List.of(new Tissue(11, 22,
                "notes", 0, null, "awdwadawdawdawd", null, null, null, null, null, null,
                null, null, "Awdawd", null, null, null, null, null, null, null,
                null, 0, 0, 0, 0, null, null, null), new  Tissue(555, 777,
                null, 0, null, null, null, null, null, "awdawd", null, null,
                null, null, "awdawddwa", null, null, null, null, null, null, null,
                null, 0, 0, 0, 0, null, null, null)));
        return fieldValue;
    }

    static class MockBaseCollectionMigrator extends BaseCollectionMigrator {

        protected RestHighLevelClient clientInstance;

        public MockBaseCollectionMigrator(String index, String realm, String object) {
            super(index, realm, object);

        }

        @Override
        protected Map<String, Object> getDataByRealm() {
            return Map.of();
        }
    }
}