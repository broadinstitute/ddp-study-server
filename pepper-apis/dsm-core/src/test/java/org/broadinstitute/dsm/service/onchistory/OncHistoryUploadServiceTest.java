package org.broadinstitute.dsm.service.onchistory;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.broadinstitute.dsm.statics.DBConstants.ADDITIONAL_VALUES_JSON;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.institution.DDPInstitutionDao;
import org.broadinstitute.dsm.db.dao.ddp.medical.records.MedicalRecordDao;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDao;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDaoImpl;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDto;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.onchistory.OncHistoryDto;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.files.parser.onchistory.OncHistoryParser;
import org.broadinstitute.dsm.files.parser.onchistory.OncHistoryParserTest;
import org.broadinstitute.dsm.util.TestUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

@Slf4j
public class OncHistoryUploadServiceTest extends DbTxnBaseTest {

    private static final String DEFAULT_REALM = "osteo2";
    private static final String LMS_REALM = "cmi-lms";
    private static final String TEST_USER = "testUser@broad.org";
    private static Map<String, ParticipantInfo> participants;
    private static DDPInstanceDto instanceDto;
    private static Map<String, DDPInstanceDto> instances;
    private static ParticipantDao participantDao;

    @BeforeClass
    public static void setup() {
        participantDao = ParticipantDao.of();
        participants = new HashMap<>();
        instances = new HashMap<>();
    }

    @AfterClass
    public static void tearDown() {
        deleteParticipants();
    }

    //@Test
    public void testGetParticipantIds() {
        setupInstance(DEFAULT_REALM);
        int participantId = createParticipant(genDdpParticipantId("ABC"), DEFAULT_REALM);
        Map<String, Integer> shortIdToId = new HashMap<>();
        shortIdToId.put("ABC", participantId);

        List<OncHistoryRecord> records = createOncHistoryRecords(shortIdToId);
        getParticipantIds(records, shortIdToId, DEFAULT_REALM);
    }

    //@Test
    public void testProcessRows() {
        setupInstance(DEFAULT_REALM);
        OncHistoryUploadService uploadService =
                new OncHistoryUploadService(DEFAULT_REALM, TEST_USER, new CodeStudyColumnsProvider());
        uploadService.initialize();

        RowReader rowReader = new RowReader();
        rowReader.read("onchistory/oncHistoryDetail.txt", uploadService);

        try {
            uploadService.validateRows(rowReader.getRows());
        } catch (Exception e) {
            Assert.fail("Exception from OncHistoryUploadService.validateRows: " + e.toString());
        }
    }

    @Test
    public void testWriteToDb() {
        writeToDb(DEFAULT_REALM, "onchistory/oncHistoryDetail.txt");
    }

    @Test
    public void testLmsWriteToDb() {
        writeToDb(LMS_REALM, "onchistory/lmsOncHistory.txt");
    }

    @Test
    public void testCreateOncHistoryRecords() {
        setupInstance(DEFAULT_REALM);
        OncHistoryUploadService uploadService =
                new OncHistoryUploadService(DEFAULT_REALM, TEST_USER, new CodeStudyColumnsProvider());
        uploadService.initialize();
        uploadService.setElasticUpdater(mockElasticUpdater());

        RowReader rowReader = new RowReader();
        rowReader.read("onchistory/oncHistoryDetail.txt", uploadService);
        List<OncHistoryRecord> rows = rowReader.getRows();
        Assert.assertEquals(3, rows.size());

        Map<String, Integer> shortIdToId = createParticipantsFromRows(rows, DEFAULT_REALM);
        // expecting two participants across three rows
        Assert.assertEquals(2, shortIdToId.size());

        OncHistoryDao oncHistoryDao = new OncHistoryDao();
        try {
            uploadService.createOncHistoryRecords(rows);
            verifyOncHistoryRecords(rows);
        } catch (Exception e) {
            Assert.fail("Exception from OncHistoryUploadService.createOncHistoryRecords: " +  getStackTrace(e));
        } finally {
            for (OncHistoryRecord row : rows) {
                int participantId = row.getParticipantId();
                Optional<OncHistoryDto> record = OncHistoryDao.getByParticipantId(participantId);
                record.ifPresent(oncHistoryDto -> oncHistoryDao.delete(oncHistoryDto.getOncHistoryId()));
            }
        }
    }

    @Ignore
    public void testGetParticipantIdForTextId() {
        setupInstance(DEFAULT_REALM);
        Map<String, Integer> knownIds = new HashMap<>();
        knownIds.put("PB9DKQ", 5564);

        /*
        OncHistoryUploadService uploadService = new OncHistoryUploadService(REALM, TEST_USER);
        for (var entry: knownIds.entrySet()) {
            try {
                int id = uploadService.getParticipantIdForTextId(entry.getKey(), "participants_structured.cmi.cmi-osteo");
                Assert.assertEquals(entry.getValue().intValue(), id);
            } catch (Exception e) {
                Assert.fail(e.toString());
            }
        }
        */
    }

    private void writeToDb(String realm, String testFile) {
        setupInstance(realm);
        OncHistoryUploadService uploadService =
                new OncHistoryUploadService(realm, TEST_USER, new CodeStudyColumnsProvider());
        uploadService.initialize();
        uploadService.setElasticUpdater(mockElasticUpdater());

        RowReader rowReader = new RowReader();
        rowReader.read(testFile, uploadService);
        List<OncHistoryRecord> rows = rowReader.getRows();
        Assert.assertEquals(3, rows.size());

        Map<String, Integer> shortIdToId = createParticipantsFromRows(rows, realm);
        // expecting two participants across three rows
        Assert.assertEquals(2, shortIdToId.size());

        try {
            uploadService.validateRows(rows);
        } catch (Exception e) {
            Assert.fail("Exception from OncHistoryUploadService.validateRows: " + e.toString());
        }

        // verify each participant ID for the study and get an associated medical record ID
        Map<Integer, Integer> participantMedIds = getParticipantIds(rows, shortIdToId, realm);
        Assert.assertEquals(2, participantMedIds.size());

        Map<String, OncHistoryUploadColumn> studyColumns = uploadService.getStudyColumns();
        OncHistoryDetailDaoImpl oncHistoryDetailDao = new OncHistoryDetailDaoImpl();
        try {
            uploadService.writeToDb(rows, participantMedIds);
            for (OncHistoryRecord row: rows) {
                Assert.assertTrue(row.getRecordId() > 0);
                OncHistoryDetailDto record = oncHistoryDetailDao.get(row.getRecordId()).orElseThrow();
                compareRecord(row, record, studyColumns);
            }
        } catch (Exception e) {
            Assert.fail("Exception from OncHistoryUploadService.writeToDb: " +  getStackTrace(e));
        } finally {
            for (OncHistoryRecord row : rows) {
                int recordId = row.getRecordId();
                if (recordId > 0) {
                    Optional<OncHistoryDetailDto> record = oncHistoryDetailDao.get(recordId);
                    oncHistoryDetailDao.delete(row.getRecordId());
                }
            }
        }

        for (OncHistoryRecord row: rows) {
            Assert.assertTrue(row.getRecordId() > 0);
        }
    }

    private static void setupInstance(String realm) {
        instanceDto = instances.get(realm);
        if (instanceDto != null) {
            return;
        }
        DDPInstanceDao instanceDao = DDPInstanceDao.of();
        // using built-in DDP instances
        instanceDto = instanceDao.getDDPInstanceByInstanceName(realm).orElseThrow();
        instances.put(realm, instanceDto);
        createFieldSettings();
    }

    private static void deleteParticipants() {
        if (participants.isEmpty()) {
            return;
        }
        MedicalRecordDao medicalRecordDao = MedicalRecordDao.of();
        DDPInstitutionDao institutionDao = DDPInstitutionDao.of();
        for (var entry: participants.entrySet()) {
            ParticipantInfo info = entry.getValue();
            MedicalRecord mr = medicalRecordDao.get(info.mrId).orElseThrow();
            int institutionId = (int) mr.getInstitutionId();
            medicalRecordDao.delete(info.mrId);
            institutionDao.delete(institutionId);
            participantDao.delete(info.participantId);
        }
    }

    private static synchronized int createParticipant(String ddpParticipantId, String realm) {
        if (participants.containsKey(ddpParticipantId)) {
            return participants.get(ddpParticipantId).participantId;
        }
        ParticipantDto.Builder builder = new ParticipantDto.Builder(instanceDto.getDdpInstanceId(), System.currentTimeMillis());
        ParticipantDto participantDto = builder.withDdpParticipantId(ddpParticipantId)
                .withLastVersionDate("")
                .withLastChanged(System.currentTimeMillis()).build();
        int participantId = participantDao.create(participantDto);

        try {
            int mrId = OncHistoryDetail.verifyOrCreateMedicalRecord(participantId, ddpParticipantId, realm, false);
            participants.put(ddpParticipantId, new ParticipantInfo(participantId, mrId));
        } catch (Exception e) {
            Assert.fail(getStackTrace(e));
        }

        return participantId;
    }

    /**
     * Create participants from a list of rows
     *
     * @return map of short ID to participant ID
     */
    private static Map<String, Integer> createParticipantsFromRows(List<OncHistoryRecord> rows, String realm) {
        Map<String, Integer> idMap = new HashMap<>();
        for (OncHistoryRecord row: rows) {
            String shortId = row.getParticipantTextId();
            Integer participantId = idMap.get(shortId);
            if (participantId == null) {
                String ddpParticipantId = genDdpParticipantId(shortId);
                participantId = createParticipant(ddpParticipantId, realm);
                idMap.put(shortId, participantId);
            }
            row.setParticipantId(participantId);
        }

        return idMap;
    }

    private static String genDdpParticipantId(String shortId) {
        return String.format("%s_%d", shortId, Instant.now().toEpochMilli());
    }

    private static List<OncHistoryRecord> getRowsFromContent(String content, OncHistoryUploadService uploadService) {
        try {
            OncHistoryParser parser = new OncHistoryParser(content, uploadService);
            return parser.parseToObjects();
        } catch (Exception e) {
            Assert.fail(getStackTrace(e));
            return null;
        }
    }

    private static String getContent(String fileName) {
        try {
            return TestUtil.readFile(fileName);
        } catch (Exception e) {
            Assert.fail("Error setting up test: " + e.toString());
            return null;
        }
    }

    private static void compareRecord(OncHistoryRecord row, OncHistoryDetailDto record,
                                      Map<String, OncHistoryUploadColumn> studyColumns) {
        Map<String, Object> recordMap = record.getColumnValues();

        Map<String, String> colValues = row.getColumns();
        for (var entry: colValues.entrySet()) {
            String value = entry.getValue();
            if (value != null) {
                Object val = recordMap.get(studyColumns.get(entry.getKey()).getColumnName());
                Assert.assertNotNull("Missing DB value for column " + entry.getKey(), val);
                Assert.assertEquals("Different value for column " + entry.getKey(), value, val.toString());
            }
        }

        String additionalValues = row.getAdditionalValuesString();
        if (additionalValues != null) {
            Object values = recordMap.get(ADDITIONAL_VALUES_JSON);
            Assert.assertNotNull("Missing DB value for column " + ADDITIONAL_VALUES_JSON, values);
            Assert.assertEquals("Different value for column " + ADDITIONAL_VALUES_JSON, additionalValues,
                    values.toString());
        }
    }

    /**
     * Verify the participant short ID, and get and record associated participant IDs and medical record IDs
     *
     * @param records list of records to update with participant IDs
     * @param shortIdToId map of short ID to participant ID
     * @return map of participant ID to med record ID
     */
    private static Map<Integer, Integer> getParticipantIds(List<OncHistoryRecord> records,
                                                           Map<String, Integer> shortIdToId, String realm) {
        TestParticipantIdProvider participantIdProvider = new TestParticipantIdProvider(shortIdToId);

        OncHistoryUploadService uploadService =
                new OncHistoryUploadService(realm, TEST_USER, new CodeStudyColumnsProvider());

        try {
            return uploadService.getParticipantIds(records, participantIdProvider, false);
        } catch (Exception e) {
            Assert.fail("Exception from OncHistoryUploadService.getParticipantIds: " + e.toString());
            return null;
        }
    }

    private static void verifyOncHistoryRecords(List<OncHistoryRecord> rows) {
        long checkTime = (Instant.now().getEpochSecond() - 120) * 1000;
        Set<Integer> participantIds = new HashSet<>();
        for (OncHistoryRecord row: rows) {
            int participantId = row.getParticipantId();
            if (participantIds.contains(participantId)) {
                continue;
            }
            Optional<OncHistoryDto> res = OncHistoryDao.getByParticipantId(participantId);
            Assert.assertTrue(res.isPresent());
            OncHistoryDto record = res.get();
            Assert.assertTrue(record.getCreated() != null && !record.getCreated().isEmpty());
            Assert.assertTrue(record.getLastChanged() > checkTime);
            Assert.assertEquals(record.getChangedBy(), TEST_USER);
        }
    }

    List<OncHistoryRecord> createOncHistoryRecords(Map<String, Integer> shortIdToId) {
        List<OncHistoryRecord> records = new ArrayList<>();
        for (var entry: shortIdToId.entrySet()) {
            records.add(new OncHistoryRecord(entry.getKey(), new HashMap<>()));
        }
        return records;
    }

    private static Map<String, Map<String, Object>> createElasticParticipantId(int participantId) {
        Map<String, Object> ptpId = new HashMap<>();
        ptpId.put("participantId", 1);
        Map<String, Object> ptp = new HashMap<>();
        ptp.put("participant", ptpId);
        Map<String, Object> dsm = new HashMap<>();
        dsm.put("dsm", ptp);
        Map<String, Map<String, Object>> top = new HashMap<>();
        top.put("top", dsm);
        return top;
    }

    private static void createFieldSettings() {
        createRequestStatusFieldSettings();

        FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();
        FieldSettingsDto.Builder builder = new FieldSettingsDto.Builder(instanceDto.getDdpInstanceId());
        FieldSettingsDto fieldSettings = builder.withFieldType("oD")
                .withColumnName("LOCAL_CONTROL")
                .withDisplayType("OPTIONS")
                .withPossibleValues("[{\"value\":\"Yes\"},{\"value\":\"No\"},{\"value\":\"Unknown\"}]").build();
        fieldSettingsDao.create(fieldSettings);

        fieldSettings = builder.withFieldType("oD")
                .withColumnName("FFPE")
                .withDisplayType("OPTIONS")
                .withPossibleValues("[{\"value\":\"Yes\"},{\"value\":\"No\"},{\"value\":\"Unknown\"}]").build();
        fieldSettingsDao.create(fieldSettings);

        fieldSettings = builder.withFieldType("oD")
                .withColumnName("DECALCIFICATION")
                .withDisplayType("OPTIONS")
                .withPossibleValues("[{\"value\":\"Nitric Acid (includes Perenyi's fluid)\"},"
                        + "{\"value\":\"Hydrochloric Acid (includes Von Ebner's solution)\"},"
                        + "{\"value\":\"Formic Acid (includes Evans/Kajian, Kristensen/Gooding/Stewart)\"},"
                        + "{\"value\":\"Acid NOS\"},{\"value\":\"EDTA\"},{\"value\":\"Sample not decalcified\"},"
                        + "{\"value\":\"Other\"},{\"value\":\"Unknown\"},{\"value\":\"Immunocal/ Soft Decal\"}]").build();
        fieldSettingsDao.create(fieldSettings);
    }

    // TODO: temporary until these are added via Liquibase
    private static void createRequestStatusFieldSettings() {
        FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();
        FieldSettingsDto.Builder builder = new FieldSettingsDto.Builder(instanceDto.getDdpInstanceId());
        FieldSettingsDto fieldSettings = builder.withFieldType("VOCABULARY")
                .withColumnName("REQUEST_STATUS")
                .withDisplayType("OPTIONS")
                .withPossibleValues("[{\"value\": \"Needs Review\"},"
                        + "{\"value\": \"Don't Request\"},"
                        + "{\"value\": \"On Hold\"},"
                        + "{\"value\": \"Request\"},"
                        + "{\"value\": \"Sent\"},"
                        + "{\"value\": \"Received\"},"
                        + "{\"value\": \"Returned\"},"
                        + "{\"value\": \"Unable to Obtain\"}]").build();
        fieldSettingsDao.create(fieldSettings);
    }

    private static OncHistoryElasticUpdater mockElasticUpdater() {
        OncHistoryElasticUpdater updater = mock(OncHistoryElasticUpdater.class);
        doNothing().when(updater).update(anyMap(), anyString());
        doNothing().when(updater).updateAppend(anyMap(), anyString());
        return updater;
    }

    @Data
    private static class RowReader {
        private List<OncHistoryRecord> rows;
        private List<Map<String, String>> rawRows;

        public void read(String fileName, OncHistoryUploadService uploadService) {
            String content = getContent(fileName);
            rows = getRowsFromContent(content, uploadService);

            // get row values for validation
            rawRows = OncHistoryParserTest.getContentRows(content, uploadService);
            Assert.assertEquals(rawRows.size(), rows.size());
        }
    }

    private static class TestParticipantIdProvider implements ParticipantIdProvider {

        private final Map<String, Integer> shortIdToId;

        public TestParticipantIdProvider(Map<String, Integer> shortIdToId) {
            this.shortIdToId = shortIdToId;
        }

        @Override
        public int getParticipantIdForShortId(String shortId) {
            return shortIdToId.get(shortId);
        }
    }

    private static class ParticipantInfo {
        public final int participantId;
        public final int mrId;

        public ParticipantInfo(int participantId, int mrId) {
            this.participantId = participantId;
            this.mrId = mrId;
        }
    }
}
