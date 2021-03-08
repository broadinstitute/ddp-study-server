package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.export.ConfiguredExport;
import org.broadinstitute.ddp.model.export.ExcludedActivityField;
import org.broadinstitute.ddp.model.export.ExcludedMetadataField;
import org.broadinstitute.ddp.model.export.ExcludedParticipantField;
import org.broadinstitute.ddp.model.export.ExportActivity;
import org.broadinstitute.ddp.model.export.ExportActivityStatusFilter;
import org.broadinstitute.ddp.model.export.ExportFilter;
import org.broadinstitute.ddp.model.export.ExportFirstField;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Test;

public class ConfiguredExportDaoTest extends TxnAwareBaseTest {
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String consentCode;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            TestDataSetupUtil.addTestConsent(handle, testData);
            consentCode = testData.getConsentActivityCode();
        });
    }

    @Test
    public void testConfiguredExport() {
        TransactionWrapper.useTxn(handle -> {
            ConfiguredExportDao dao = handle.attach(ConfiguredExportDao.class);
            long studyId = testData.getStudyId();

            // Test disabled export
            long exportId = testExportConfiguration(dao, studyId, false, null, null, null, null);
            dao.deleteConfiguredExportById(exportId);

            // Test enabled configured export: enabled, schedule, bucket type, bucket name, file path
            exportId = testExportConfiguration(dao, studyId, true, "0 15 3 ? * *", "NONCRED",
                    "BucketNameHere", "TheBucket/Path");

            //Test excluded participant field
            long excludedFieldId = testExcludedParticipantField(dao, exportId, "FIRST_NAME");
            long excludedFieldId2 = testExcludedParticipantField(dao, exportId, "LAST_NAME");

            //Test export activity
            long activityId = testExportActivity(dao, exportId, studyId, consentCode, testData.getConsentActivityId());

            //Test excluded activity field
            long activityFieldId = testExcludedActivityField(dao, activityId);

            //Test excluded metadata field
            long metadataFieldId = testExcludedMetadataField(dao, activityId);

            //Test first field
            long firstFieldId = testExportFirstField(dao, activityId);

            //Test filter
            long filterId = testExportFilter(dao, activityId);

            //Test activity status filter
            long activityStatusFilterId = testExportActivityStatusFilter(dao, filterId);

            //Test finding everything based on study Id
            testRetrieval(dao, studyId, exportId, excludedFieldId, excludedFieldId2, activityId, activityFieldId, metadataFieldId,
                    firstFieldId, filterId, activityStatusFilterId);

            dao.deleteFullConfiguredExportByStudyId(studyId);
        });
    }

    private void testRetrieval(ConfiguredExportDao dao, long studyId, long exportId, long excludedFieldId, long excludedFieldId2,
                               long activityId, long activityFieldId, long metadataFieldId, long firstFieldId, long filterId,
                               long activityStatusFilterId) {
        Optional<ConfiguredExport> export = dao.findExportByStudyId(studyId);
        assertTrue(export.isPresent());
        assertEquals(export.get().getId(), exportId);

        List<ExcludedParticipantField> excludedParticipantFields = dao.findExcludedParticipantFieldsByExportId(exportId);
        assertNotNull(excludedParticipantFields);
        assertEquals(2, excludedParticipantFields.size());
        assertTrue(excludedParticipantFields.stream().anyMatch(field -> field.getId() == excludedFieldId));
        assertTrue(excludedParticipantFields.stream().anyMatch(field -> field.getId() == excludedFieldId2));

        List<ExportActivity> activities = dao.findActivitiesByExportId(exportId);
        assertNotNull(activities);
        assertEquals(1, activities.size());
        assertEquals(activityId, activities.get(0).getId());

        List<ExcludedActivityField> activityFields = dao.findExcludedActivityFieldByActivityIds(activityId);
        assertNotNull(activityFields);
        assertEquals(1, activityFields.size());
        assertEquals(activityFieldId, activityFields.get(0).getId());

        List<ExcludedMetadataField> metadataFields = dao.findExcludedMetadataFieldsByActivityId(activityId);
        assertNotNull(metadataFields);
        assertEquals(1, metadataFields.size());
        assertEquals(metadataFieldId, metadataFields.get(0).getId());

        List<ExportFirstField> firstFields = dao.findFirstFieldsByActivityId(activityId);
        assertNotNull(firstFields);
        assertEquals(1, firstFields.size());
        assertEquals(firstFieldId, firstFields.get(0).getId());

        List<ExportFilter> filters = dao.findFiltersByActivityId(activityId);
        assertNotNull(filters);
        assertEquals(1, filters.size());
        assertEquals(filterId, filters.get(0).getId());

        List<ExportActivityStatusFilter> activityStatusFilters = dao.findActivityStatusFiltersByFilterId(filterId);
        assertNotNull(activityStatusFilters);
        assertEquals(1, activityStatusFilters.size());
        assertEquals(activityStatusFilterId, activityStatusFilters.get(0).getId());
    }

    private long testExportActivityStatusFilter(ConfiguredExportDao dao, long filterId) {
        ExportActivityStatusFilter filterA = new ExportActivityStatusFilter(filterId, "COMPLETE");
        long statusFilterId = dao.createExportActivityStatusFilter(filterA).getId();
        Optional<ExportActivityStatusFilter> opt = dao.findActivityStatusFilterById(statusFilterId);
        assertTrue(opt.isPresent());
        ExportActivityStatusFilter filterB = opt.get();
        assertEquals(filterA.getFilterId(), filterB.getFilterId());
        Optional<Long> typeId = dao.getActivityStatusIDByCode(filterA.getStatusType());
        assertTrue(typeId.isPresent());
        long expectedFilterStatusTypeId = typeId.get();
        assertEquals(expectedFilterStatusTypeId, filterB.getStatusTypeId());
        return statusFilterId;
    }

    private long testExportFilter(ConfiguredExportDao dao, long activityId) {
        ExportFilter filterA = new ExportFilter(activityId, "ACTIVITY_STATUS");
        long filterId = dao.createExportFilter(filterA).getId();
        Optional<ExportFilter> opt = dao.findFilterById(filterId);
        assertTrue(opt.isPresent());
        ExportFilter filterB = opt.get();
        assertEquals(filterA.getExportActivityId(), filterB.getExportActivityId());

        Optional<Long> typeId = dao.getExportFilterTypeIdFromType(filterA.getFilterType());
        assertTrue(typeId.isPresent());
        long expectedFilterTypeId = typeId.get();
        assertEquals(expectedFilterTypeId, filterB.getFilterTypeId());
        return filterId;
    }

    private long testExportFirstField(ConfiguredExportDao dao, long activityId) {
        ExportFirstField fieldA = new ExportFirstField(activityId, "FAVORITE_PLANT");
        long fieldId = dao.createExportFirstField(fieldA).getId();
        Optional<ExportFirstField> opt = dao.findFirstFieldById(fieldId);
        assertTrue(opt.isPresent());
        ExportFirstField fieldB = opt.get();
        assertEquals(fieldA.getActivityId(), fieldB.getActivityId());
        assertEquals(fieldA.getFirstField(), fieldB.getFirstField());
        return fieldId;
    }

    private long testExcludedMetadataField(ConfiguredExportDao dao, long metadataId) {
        ExcludedMetadataField fieldA = new ExcludedMetadataField(metadataId, "DATE_COMPLETED");
        long fieldId = dao.createExcludedMetadataField(fieldA).getId();
        Optional<ExcludedMetadataField> opt = dao.findExcludedMetadataFieldById(fieldId);
        assertTrue(opt.isPresent());
        ExcludedMetadataField fieldB = opt.get();
        assertEquals(fieldA.getActivityId(), fieldB.getActivityId());
        assertEquals(fieldA.getExcludedMetadataField(), fieldB.getExcludedMetadataField());
        return fieldId;
    }

    private long testExcludedActivityField(ConfiguredExportDao dao, long activityId) {
        ExcludedActivityField fieldA = new ExcludedActivityField(activityId, "FAVORITE_COLOR");
        long fieldId = dao.createExcludedActivityField(fieldA).getId();
        Optional<ExcludedActivityField> opt = dao.findExcludedActivityFieldById(fieldId);
        assertTrue(opt.isPresent());
        ExcludedActivityField fieldB = opt.get();
        assertEquals(fieldA.getActivityId(), fieldB.getActivityId());
        assertEquals(fieldA.getExcludedActivityField(), fieldB.getExcludedActivityField());
        return fieldId;
    }

    private long testExportActivity(ConfiguredExportDao dao, long exportId, long studyId, String activityCode, long studyActivityId) {
        ExportActivity fieldA = new ExportActivity(exportId, activityCode, true, studyId);
        long activityId = dao.createExportActivity(fieldA).getId();
        Optional<ExportActivity> opt = dao.findActivityById(activityId);
        assertTrue(opt.isPresent());
        ExportActivity fieldB = opt.get();
        assertEquals(fieldA.getExportId(), fieldB.getExportId());
        assertEquals(studyActivityId, fieldB.getActivityId());
        assertEquals(fieldA.isIncremental(), fieldB.isIncremental());
        return activityId;
    }

    private long testExcludedParticipantField(ConfiguredExportDao dao, long exportId, String fieldName) {
        ExcludedParticipantField fieldA = new ExcludedParticipantField(exportId, fieldName);
        long fieldId = dao.createExcludedParticipantField(fieldA).getId();
        Optional<ExcludedParticipantField> opt = dao.findExcludedParticipantFieldById(fieldId);
        assertTrue(opt.isPresent());
        ExcludedParticipantField fieldB = opt.get();
        assertEquals(fieldA.getExportId(), fieldB.getExportId());
        assertEquals(fieldA.getExcludedParticipantField(), fieldB.getExcludedParticipantField());
        return fieldId;
    }

    private long testExportConfiguration(ConfiguredExportDao dao, long studyId, boolean isEnabled, String runSchedule,
                                         String bucketType, String bucketName, String filePath) {
        ConfiguredExport exportA = new ConfiguredExport(studyId, isEnabled, runSchedule, bucketType, bucketName, filePath);
        long exportId = dao.createConfiguredExport(exportA).getId();
        Optional<ConfiguredExport> opt = dao.findExportById(exportId);
        assertTrue(opt.isPresent());
        ConfiguredExport exportB = opt.get();
        assertEquals(exportA.getEnabled(), exportB.getEnabled());
        assertEquals(exportA.getStudyId(), exportB.getStudyId());
        assertEquals(exportA.getRunSchedule(), exportB.getRunSchedule());
        assertEquals(exportA.getBucketType(), exportB.getBucketType());
        assertEquals(exportA.getBucketName(), exportB.getBucketName());
        assertEquals(exportA.getFilePath(), exportB.getFilePath());
        return exportId;
    }
}
