package org.broadinstitute.dsm.model.elastic.migration;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class BaseMigratorTest {

    @Test
    public void testGetEsDataForEntity() {
        Map<String, Object> fieldMap1 = Map.of("field1", "value1");
        Map<String, Object> fieldMap2 = Map.of("field2", "value2");
        Map<String, Object> fieldMap3 = Map.of("field3", "value3");

        Map<String, Map<String, Object>> ptpToEsData = Map.of(
                "ptp1", Map.of(
                        "medicalRecord", List.of(fieldMap1, fieldMap2),
                        "oncHistoryDetail", List.of(fieldMap3),
                        "oncHistory", fieldMap2),
                "ptp2", Map.of(
                        "oncHistoryDetail", List.of(fieldMap2),
                        "oncHistory", fieldMap1,
                        "tissue", Collections.emptyList()));

        Map<String, Map<String, Object>> ptpEntityData =
                BaseMigrator.getEsDataForEntity(ptpToEsData, "medicalRecord");
        Assert.assertEquals(1, ptpEntityData.size());
        Map<String, Object> entityData = ptpEntityData.get("ptp1");
        Assert.assertNotNull(entityData);
        Assert.assertTrue(entityData.containsKey("medicalRecord"));
        Assert.assertFalse(entityData.containsKey("oncHistory"));

        ptpEntityData = BaseMigrator.getEsDataForEntity(ptpToEsData, "oncHistory");
        Assert.assertEquals(2, ptpEntityData.size());
        entityData = ptpEntityData.get("ptp2");
        Assert.assertNotNull(entityData);
        Assert.assertTrue(entityData.containsKey("oncHistory"));
        Assert.assertFalse(entityData.containsKey("medicalRecord"));
        Map<String, Object> entityMap = (Map)entityData.get("oncHistory");
        Assert.assertEquals(fieldMap1, entityMap);

        // empty entity list
        ptpEntityData = BaseMigrator.getEsDataForEntity(ptpToEsData, "tissue");
        Assert.assertTrue(ptpEntityData.isEmpty());

        // non-existent entity
        ptpEntityData = BaseMigrator.getEsDataForEntity(ptpToEsData, "cohortTag");
        Assert.assertTrue(ptpEntityData.isEmpty());
    }

    @Test
    public void testReplaceLegacyPIDs() {
        Map<String, String> legacyIdToPid = Map.of(
                "legacy1", "pid1",
                "legacy2", "pid2");

        Map<String, Object> participantRecords = Map.of(
                "pid3", List.of("str1", "str2"),
                "legacy1", List.of("str1", "str2"),
                "legacy2", List.of("str1"));

        // regular case
        MockCollectionMigrator migrator = new MockCollectionMigrator();
        Map<String, Object> updatedRecords = migrator.replaceLegacyPIDs(participantRecords, legacyIdToPid);
        Assert.assertEquals(3, updatedRecords.size());
        Assert.assertEquals(participantRecords.get("pid3"), updatedRecords.get("pid3"));
        Assert.assertEquals(participantRecords.get("legacy1"), updatedRecords.get("pid1"));
        Assert.assertEquals(participantRecords.get("legacy2"), updatedRecords.get("pid2"));

        // merge case
        Map<String, Object> ptpRecords2 = new HashMap<>(participantRecords);
        ptpRecords2.put("pid2", List.of("str3", "str4"));
        Map<String, Object> updated2 = migrator.replaceLegacyPIDs(ptpRecords2, legacyIdToPid);
        Assert.assertEquals(3, updated2.size());
        Assert.assertEquals(participantRecords.get("pid3"), updated2.get("pid3"));
        Assert.assertEquals(participantRecords.get("legacy1"), updated2.get("pid1"));
        Assert.assertEquals(List.of("str1", "str3", "str4"), updated2.get("pid2"));
    }
}
