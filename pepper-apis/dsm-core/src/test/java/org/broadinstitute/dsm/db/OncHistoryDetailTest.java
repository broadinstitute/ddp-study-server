package org.broadinstitute.dsm.db;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.institution.DDPInstitutionDao;
import org.broadinstitute.dsm.db.dao.ddp.medical.records.MedicalRecordDao;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDaoImpl;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDto;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.util.DBTestUtil;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class OncHistoryDetailTest extends DbAndElasticBaseTest {

    private static final String TEST_USER = "TEST_USER";
    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static DDPInstanceDto ddpInstanceDto;
    private static String instanceName;
    private static String esIndex;
    private static ParticipantDto testParticipant = null;

    @BeforeClass
    public static void setup() throws Exception {
        instanceName = "onchistorydetailtest";
        esIndex = ElasticTestUtil.createIndexWithMappings(instanceName, "elastic/lmsMappings.json");
        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
    }

    @AfterClass
    public static void tearDown() {
        ddpInstanceDao.delete(ddpInstanceDto.getDdpInstanceId());
        ElasticTestUtil.deleteIndex(esIndex);
    }

    @After
    public void deleteParticipants() {
        if (testParticipant != null) {
            TestParticipantUtil.deleteParticipant(testParticipant.getParticipantId().orElseThrow());
            testParticipant = null;
        }
    }

    @Test
    public void updateDestructionPolicyTest() {
        String ddpParticipantId = TestParticipantUtil.genDDPParticipantId("OncHistoryDetailTest");
        testParticipant = TestParticipantUtil.createParticipant(ddpParticipantId, ddpInstanceDto.getDdpInstanceId());
        ElasticTestUtil.createParticipant(esIndex, testParticipant);
        ElasticTestUtil.addParticipantProfileFromFile(esIndex, "elastic/participantProfile.json", ddpParticipantId);

        int medicalRecordId = OncHistoryDetail.verifyOrCreateMedicalRecord(testParticipant.getParticipantId().orElseThrow(),
                ddpParticipantId, instanceName, true);

        log.debug("ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));

        ParticipantDto ptp2 = null;
        int ptp2MedicalRecordId = -1;
        int recId1 = -1;
        int recId2 = -1;
        int recId3 = -1;
        OncHistoryDetailDaoImpl oncHistoryDetailDao = new OncHistoryDetailDaoImpl();
        try {
            // create another ptp with a medical record but no onc history
            String ddpParticipantId2 = TestParticipantUtil.genDDPParticipantId("ptp2_OncHistoryDetailTest");
            ptp2 = TestParticipantUtil.createParticipant(ddpParticipantId2, ddpInstanceDto.getDdpInstanceId());
            ElasticTestUtil.createParticipant(esIndex, ptp2);
            ElasticTestUtil.addParticipantProfileFromFile(esIndex, "elastic/participantProfile.json", ddpParticipantId2);

            log.debug("ES participant record for {}: {}", ddpParticipantId2,
                    ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId2));
            ptp2MedicalRecordId = OncHistoryDetail.verifyOrCreateMedicalRecord(ptp2.getParticipantId().orElseThrow(),
                    ddpParticipantId2, instanceName, true);

            // add some onc history detail records
            OncHistoryDetail.Builder builder = new OncHistoryDetail.Builder()
                    .withDdpInstanceId(ddpInstanceDto.getDdpInstanceId())
                    .withMedicalRecordId(medicalRecordId)
                    .withFacility("Office")
                    .withDestructionPolicy("12")
                    .withChangedBy(TEST_USER);

            OncHistoryDetail rec1 = builder.build();
            recId1 = OncHistoryDetail.createOncHistoryDetail(rec1);
            rec1.setOncHistoryDetailId(recId1);
            ElasticTestUtil.createOncHistoryDetail(esIndex, rec1, ddpParticipantId);

            builder.withDestructionPolicy("3");
            OncHistoryDetail rec2 = builder.build();
            recId2 = OncHistoryDetail.createOncHistoryDetail(rec2);
            rec2.setOncHistoryDetailId(recId2);
            ElasticTestUtil.createOncHistoryDetail(esIndex, rec2, ddpParticipantId);

            builder.withFacility("Other office");
            OncHistoryDetail rec3 = builder.build();
            recId3 = OncHistoryDetail.createOncHistoryDetail(rec3);
            rec3.setOncHistoryDetailId(recId3);
            ElasticTestUtil.createOncHistoryDetail(esIndex, rec3, ddpParticipantId);

            // update and verify
            OncHistoryDetail.updateDestructionPolicy("5", "Office", instanceName, TEST_USER);

            OncHistoryDetailDto updateRec1 = oncHistoryDetailDao.get(recId1).orElseThrow();
            Assert.assertEquals("5", updateRec1.getColumnValues().get("destruction_policy"));
            OncHistoryDetailDto updateRec2 = oncHistoryDetailDao.get(recId2).orElseThrow();
            Assert.assertEquals("5", updateRec2.getColumnValues().get("destruction_policy"));
            OncHistoryDetailDto updateRec3 = oncHistoryDetailDao.get(recId3).orElseThrow();
            Assert.assertEquals("3", updateRec3.getColumnValues().get("destruction_policy"));

            ElasticSearchParticipantDto esParticipant =
                    ElasticSearchUtil.getParticipantESDataByParticipantId(esIndex, ddpParticipantId);
            Dsm dsm = esParticipant.getDsm().orElseThrow();

            List<OncHistoryDetail> oncHistoryDetailList = dsm.getOncHistoryDetail();
            Assert.assertEquals(3, oncHistoryDetailList.size());
            for (OncHistoryDetail oncHistoryDetail: oncHistoryDetailList) {
                int recId = oncHistoryDetail.getOncHistoryDetailId();
                if (recId == recId1 || recId == recId2) {
                    Assert.assertEquals("5", oncHistoryDetail.getDestructionPolicy());
                } else if (recId == recId3) {
                    Assert.assertEquals("3", oncHistoryDetail.getDestructionPolicy());
                } else {
                    Assert.fail("Unknown oncHistoryDetail record ID " + recId);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception" + e);
        } finally {
            log.info("recId1={}, ptp2MedicalRecordId={}, ptp2={}", recId1, ptp2MedicalRecordId, ptp2);
            if (recId1 != -1) {
                oncHistoryDetailDao.delete(recId1);
            }
            if (recId2 != -1) {
                oncHistoryDetailDao.delete(recId2);
            }
            if (recId3 != -1) {
                oncHistoryDetailDao.delete(recId3);
            }
            deleteMedicalRecord(medicalRecordId);

            // ptp2
            if (ptp2MedicalRecordId != -1) {
                deleteMedicalRecord(ptp2MedicalRecordId);
            }
            if (ptp2 != null) {
                TestParticipantUtil.deleteParticipant(ptp2.getParticipantId().orElseThrow());
            }
        }
    }

    private static void deleteMedicalRecord(int medicalRecordId) {
        MedicalRecordDao medicalRecordDao = new MedicalRecordDao();
        MedicalRecord medicalRecord = medicalRecordDao.get(medicalRecordId).get();
        medicalRecordDao.delete(medicalRecordId);
        DDPInstitutionDao ddpInstitutionDao = new DDPInstitutionDao();
        ddpInstitutionDao.delete(medicalRecord.getInstitutionId());
    }
}
