package org.broadinstitute.dsm.service.onchistory;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.institution.DDPInstitutionDao;
import org.broadinstitute.dsm.db.dao.ddp.medical.records.MedicalRecordDao;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDaoImpl;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
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

    private static final String REALM = "osteo2";
    private static final String TEST_USER = "testUser";
    private static Map<Integer, Integer> participants;
    private static DDPInstanceDto instanceDto;
    private static ParticipantDao participantDao;

    @BeforeClass
    public static void setup() {
        DDPInstanceDao instanceDao = DDPInstanceDao.of();
        // use a built-in DDP instance
        instanceDto = instanceDao.getDDPInstanceByInstanceName(REALM).orElseThrow();
        participantDao = ParticipantDao.of();
        participants = new HashMap<>();
        createFieldSettings();
    }

    @AfterClass
    public static void tearDown() {
        deleteParticipants();
    }

    //@Test
    public void testGetParticipantIds() {
        int participantId = createParticipant(genDdpParticipantId("ABC"));
        Map<String, Integer> shortIdToId = new HashMap<>();
        shortIdToId.put("ABC", participantId);

        List<OncHistoryRecord> records = createOncHistoryRecords(shortIdToId);
        getParticipantIds(records, shortIdToId);
    }

    //@Test
    public void testProcessRows() {
        OncHistoryUploadService uploadService =
                new OncHistoryUploadService(REALM, TEST_USER, new CodeStudyColumnsProvider());
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
        OncHistoryUploadService uploadService =
                new OncHistoryUploadService(REALM, TEST_USER, new CodeStudyColumnsProvider());
        uploadService.initialize();

        RowReader rowReader = new RowReader();
        rowReader.read("onchistory/oncHistoryDetail.txt", uploadService);
        List<OncHistoryRecord> rows = rowReader.getRows();
        Assert.assertEquals(3, rows.size());

        Map<String, Integer> shortIdToId = createParticipantsFromRows(rows);
        // expecting two participants across three rows
        Assert.assertEquals(2, shortIdToId.size());

        try {
            uploadService.validateRows(rows);
        } catch (Exception e) {
            Assert.fail("Exception from OncHistoryUploadService.validateRows: " + e.toString());
        }

        // verify each participant ID for the study and get an associated medical record ID
        Map<Integer, Integer> participantMedIds = getParticipantIds(rows, shortIdToId);
        Assert.assertEquals(2, participantMedIds.size());

        try {
            uploadService.writeToDb(rows, participantMedIds);
        } catch (Exception e) {
            Assert.fail("Exception from OncHistoryUploadService.writeToDb: " +  getStackTrace(e));
        } finally {
            OncHistoryDetailDaoImpl oncHistoryDetailDao = new OncHistoryDetailDaoImpl();
            for (OncHistoryRecord row : rows) {
                if (row.getRecordId() > 0) {
                    oncHistoryDetailDao.delete(row.getRecordId());
                }
            }
        }

        for (OncHistoryRecord row: rows) {
            Assert.assertTrue(row.getRecordId() > 0);
        }
    }

    @Ignore
    public void testGetParticipantIdForTextId() {
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

    private static void deleteParticipants() {
        if (participants.isEmpty()) {
            return;
        }
        MedicalRecordDao medicalRecordDao = MedicalRecordDao.of();
        DDPInstitutionDao institutionDao = DDPInstitutionDao.of();
        for (var entry: participants.entrySet()) {
            int mrId = entry.getValue();
            MedicalRecord mr = medicalRecordDao.get(mrId).orElseThrow();
            int institutionId = (int) mr.getInstitutionId();
            medicalRecordDao.delete(mrId);
            institutionDao.delete(institutionId);
            participantDao.delete(entry.getKey());
        }
    }

    private static int createParticipant(String ddpParticipantId) {
        ParticipantDto.Builder builder = new ParticipantDto.Builder(instanceDto.getDdpInstanceId(), System.currentTimeMillis());
        ParticipantDto participantDto = builder.withDdpParticipantId(ddpParticipantId)
                .withLastVersionDate("")
                .withLastChanged(System.currentTimeMillis()).build();
        int participantId = participantDao.create(participantDto);

        try {
            int mrId = OncHistoryDetail.verifyOrCreateMedicalRecord(participantId, ddpParticipantId, REALM, false);
            participants.put(participantId, mrId);
        } catch (Exception e) {
            Assert.fail(getStackTrace(e));
        }

        return participantId;
    }

    private static Map<String, Integer> createParticipantsFromRows(List<OncHistoryRecord> rows) {
        Map<String, Integer> idMap = new HashMap<>();
        for (OncHistoryRecord row: rows) {
            String shortId = row.getParticipantTextId();
            if (idMap.get(shortId) == null) {
                String ddpParticipantId = genDdpParticipantId(shortId);
                int participantId = createParticipant(ddpParticipantId);
                idMap.put(shortId, participantId);
            }
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

    /**
     * Verify the participant short ID, and get and record associated participant IDs and medical record IDs
     *
     * @param records list of records to update with participant IDs
     * @param shortIdToId map of short ID to participant ID
     * @return map of participant ID to med record ID
     */
    private static Map<Integer, Integer> getParticipantIds(List<OncHistoryRecord> records,
                                                           Map<String, Integer> shortIdToId) {
        TestParticipantIdProvider participantIdProvider = new TestParticipantIdProvider(shortIdToId);

        OncHistoryUploadService uploadService =
                new OncHistoryUploadService(REALM, TEST_USER, new CodeStudyColumnsProvider());

        try {
            return uploadService.getParticipantIds(records, participantIdProvider, false);
        } catch (Exception e) {
            Assert.fail("Exception from OncHistoryUploadService.getParticipantIds: " + e.toString());
            return null;
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
                .withPossibleValues("[{\"value\":\"Nitric Acid (includes Perenyi\\u0027s fluid)\"},"
                        + "{\"value\":\"Hydrochloric Acid (includes Von Ebner\\u0027s solution)\"},"
                        + "{\"value\":\"Formic Acid (includes Evans/Kajian, Kristensen/Gooding/Stewart)\"},"
                        + "{\"value\":\"Acid NOS\"},{\"value\":\"EDTA\"},{\"value\":\"Sample not decalcified\"},"
                        + "{\"value\":\"Other\"},{\"value\":\"Unknown\"},{\"value\":\"Immunocal/ Soft Decal\"}]").build();
        fieldSettingsDao.create(fieldSettings);
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
}
