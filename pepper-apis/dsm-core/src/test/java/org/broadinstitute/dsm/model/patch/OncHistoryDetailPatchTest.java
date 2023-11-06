package org.broadinstitute.dsm.model.patch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.institution.DDPInstitutionDao;
import org.broadinstitute.dsm.db.dao.ddp.medical.records.MedicalRecordDao;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDao;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDaoImpl;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDto;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantRecordDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantRecordDto;
import org.broadinstitute.dsm.db.dto.onchistory.OncHistoryDto;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.broadinstitute.dsm.util.TestUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class OncHistoryDetailPatchTest extends DbAndElasticBaseTest {

    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static final String instanceName = "onchistorydetailpatchtest";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static ParticipantDto testParticipant = null;

    @BeforeClass
    public static void setup() throws Exception {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/lmsMappings.json", null);
        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
    }

    @AfterClass
    public static void tearDown() {
        try {
            Assert.assertNull(testParticipant);
            ddpInstanceDao.delete(ddpInstanceDto.getDdpInstanceId());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception: " + e);
        }
        ElasticTestUtil.deleteIndex(esIndex);
    }

    @After
    public void deleteParticipants() {
        if (testParticipant != null) {
            TestParticipantUtil.deleteParticipant(testParticipant.getParticipantIdOrThrow());
            testParticipant = null;
        }
    }

    @Test
    public void doPatchTest() {
        String ddpParticipantId = TestParticipantUtil.genDDPParticipantId("OncHistoryDetailPatchTest");
        try {
            testParticipant = TestParticipantUtil.createParticipant(ddpParticipantId, ddpInstanceDto.getDdpInstanceId());
            ElasticTestUtil.createParticipant(esIndex, testParticipant);
            ElasticTestUtil.addInstitutionAndMedicalRecord(testParticipant, ddpInstanceDto);

            Profile profile = ElasticTestUtil.addParticipantProfileFromFile(esIndex, "elastic/participantProfile.json",
                    ddpParticipantId);

            String patchJson = TestUtil.readFile("onchistory/patch.json");
            Gson gson = new Gson();
            Patch patch = gson.fromJson(patchJson, Patch.class);
            patch.setDdpParticipantId(ddpParticipantId);
            patch.setParentId(Integer.toString(testParticipant.getParticipantIdOrThrow()));
            patch.setRealm(instanceName);
            patch.setId(null);

            OncHistoryDetailPatch oncHistoryDetailPatch = new OncHistoryDetailPatch(patch);
            oncHistoryDetailPatch.setElasticSearchExportable(true);
            oncHistoryDetailPatch.doPatch();

            // TODO: ElasticDataExportAdapter should probably force a refresh, but for now...
            Thread.sleep(1000);
            log.debug("Participant document with oncHistory patch: {}",
                    ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));

            ElasticSearchParticipantDto esParticipant =
                     ElasticSearchUtil.getParticipantESDataByParticipantId(esIndex, ddpParticipantId);
            Profile esProfile = esParticipant.getProfile().orElseThrow();
            Assert.assertEquals(profile.getHruid(), esProfile.getHruid());

            Dsm dsm = esParticipant.getDsm().orElseThrow();
            List<MedicalRecord> medRecords = dsm.getMedicalRecord();
            log.debug("Med records {}", medRecords);
            Assert.assertEquals(2, medRecords.size());

            List<OncHistoryDetail> oncHistoryDetailList = dsm.getOncHistoryDetail();
            Assert.assertEquals(1, oncHistoryDetailList.size());
            OncHistoryDetail oncHistoryDetail = oncHistoryDetailList.get(0);
            log.debug("Onc history detail {}", oncHistoryDetail);

            Map<String, String> patchMap = nameValuesToMap(patch.getNameValues());
            Assert.assertEquals(patchMap.get("oD.destructionPolicy"), oncHistoryDetail.getDestructionPolicy());
            Assert.assertEquals(patchMap.get("oD.facility"), oncHistoryDetail.getFacility());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception: " + e);
        } finally {
            DDPInstitutionDao institutionDao = new DDPInstitutionDao();
            MedicalRecordDao medicalRecordDao = new MedicalRecordDao();
            List<MedicalRecord> medRecords =
                    MedicalRecord.getMedicalRecordsForParticipant(testParticipant.getParticipantId().orElseThrow());
            for (MedicalRecord medRecord: medRecords) {
                List<OncHistoryDetailDto> oncHistoryDetailList =
                        OncHistoryDetail.getOncHistoryDetailByMedicalRecord(medRecord.getMedicalRecordId());
                log.info("For med record {} oncHistoryDetailList.size() {}", medRecord.getMedicalRecordId(), oncHistoryDetailList.size());
                OncHistoryDetailDaoImpl oncHistoryDetailDao = new OncHistoryDetailDaoImpl();
                for (var ohd: oncHistoryDetailList) {
                    oncHistoryDetailDao.delete((Integer)ohd.getColumnValues().get(DBConstants.ONC_HISTORY_DETAIL_ID));
                }
                // the schema allows multiple medical records for an institution, so this will just
                // fail silently if the institution is already deleted
                medicalRecordDao.delete(medRecord.getMedicalRecordId());
                institutionDao.delete(medRecord.getInstitutionId());
            }
            int participantId = testParticipant.getParticipantIdOrThrow();
            Optional<ParticipantRecordDto> recordDto = ParticipantRecordDao.of()
                    .getParticipantRecordByParticipantId(participantId);
            recordDto.ifPresent(participantRecordDto -> ParticipantRecordDao.of()
                    .delete(participantRecordDto.getParticipantRecordId().orElseThrow()));
            Optional<OncHistoryDto> oncHistory = OncHistoryDao.getByParticipantId(participantId);
            oncHistory.ifPresent(oncHistoryDto -> {
                OncHistoryDao ohDao = new OncHistoryDao();
                ohDao.delete(oncHistoryDto.getOncHistoryId());
            });
        }
    }

    private static Map<String, String> nameValuesToMap(List<NameValue> nameValues) {
        Map<String, String> nameToValue = new HashMap<>();
        for (var nameValue: nameValues) {
            nameToValue.put(nameValue.getName(), nameValue.getValue().toString());
        }
        return nameToValue;
    }
}
