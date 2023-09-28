package org.broadinstitute.dsm.db;

import java.time.Instant;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.institution.DDPInstitutionDao;
import org.broadinstitute.dsm.db.dao.ddp.medical.records.MedicalRecordDao;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDaoImpl;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDto;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.util.DBTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class OncHistoryDetailTest extends DbTxnBaseTest {

    private static final String TEST_USER = "TEST_USER";
    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static DDPInstanceDto ddpInstanceDto;
    private static ParticipantDto testParticipant = null;

    @BeforeClass
    public static void setup() throws Exception {
        String instanceName = String.format("ReferralSourceServiceTest_%d", Instant.now().toEpochMilli());
        ddpInstanceDto = DBTestUtil.createTestDdpInstance(ddpInstanceDao, instanceName);
    }

    @AfterClass
    public static void tearDown() {
        ddpInstanceDao.delete(ddpInstanceDto.getDdpInstanceId());
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

        int medicalRecordId = OncHistoryDetail.verifyOrCreateMedicalRecord(testParticipant.getParticipantId().orElseThrow(),
                ddpParticipantId, ddpInstanceDto.getInstanceName(), false);

        // add some onc history detail records
        OncHistoryDetail.Builder builder = new OncHistoryDetail.Builder()
                .withMedicalRecordId(medicalRecordId)
                .withFacility("Office")
                .withDestructionPolicy("12")
                .withChangedBy(TEST_USER);

        int recId1 = -1;
        int recId2 = -1;
        int recId3 = -1;
        OncHistoryDetailDaoImpl oncHistoryDetailDao = new OncHistoryDetailDaoImpl();
        try {
            OncHistoryDetail rec1 = builder.build();
            recId1 = OncHistoryDetail.createOncHistoryDetail(rec1);

            builder.withDestructionPolicy("3");
            OncHistoryDetail rec2 = builder.build();
            recId2 = OncHistoryDetail.createOncHistoryDetail(rec2);

            builder.withFacility("Other office");
            OncHistoryDetail rec3 = builder.build();
            recId3 = OncHistoryDetail.createOncHistoryDetail(rec3);

            // update and verify
            OncHistoryDetail.updateDestructionPolicy("5", "Office", ddpInstanceDto.getInstanceName(), TEST_USER, false);

            OncHistoryDetailDto updateRec1 = oncHistoryDetailDao.get(recId1).orElseThrow();
            Assert.assertEquals("5", updateRec1.getColumnValues().get("destruction_policy"));
            OncHistoryDetailDto updateRec2 = oncHistoryDetailDao.get(recId2).orElseThrow();
            Assert.assertEquals("5", updateRec2.getColumnValues().get("destruction_policy"));
            OncHistoryDetailDto updateRec3 = oncHistoryDetailDao.get(recId3).orElseThrow();
            Assert.assertEquals("3", updateRec3.getColumnValues().get("destruction_policy"));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception" + e);
        } finally {
            if (recId1 != -1) {
                oncHistoryDetailDao.delete(recId1);
            }
            if (recId2 != -1) {
                oncHistoryDetailDao.delete(recId2);
            }
            if (recId3 != -1) {
                oncHistoryDetailDao.delete(recId3);
            }
            MedicalRecordDao medicalRecordDao = new MedicalRecordDao();
            MedicalRecord medicalRecord = medicalRecordDao.get(medicalRecordId).get();
            medicalRecordDao.delete(medicalRecordId);
            DDPInstitutionDao ddpInstitutionDao = new DDPInstitutionDao();
            ddpInstitutionDao.delete(medicalRecord.getInstitutionId());
        }
    }
}
