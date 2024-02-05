package org.broadinstitute.dsm.db;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDaoImpl;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDto;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.MedicalRecordTestUtil;
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
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/lmsMappings.json", null);
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
        testParticipant = TestParticipantUtil.createParticipantWithEsProfile("ptp1_onchistory", ddpInstanceDto, esIndex);
        String ddpParticipantId = testParticipant.getDdpParticipantIdOrThrow();
        int medicalRecordId = MedicalRecordTestUtil.createMedicalRecord(testParticipant, ddpInstanceDto);

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
            ptp2 = TestParticipantUtil.createParticipantWithEsProfile("ptp2_onchistory", ddpInstanceDto, esIndex);
            String ddpParticipantId2 = ptp2.getDdpParticipantIdOrThrow();

            log.debug("ES participant record for {}: {}", ddpParticipantId2,
                    ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId2));
            ptp2MedicalRecordId = MedicalRecordTestUtil.createMedicalRecord(ptp2, ddpInstanceDto);

            // add some onc history detail records
            OncHistoryDetail.Builder builder = new OncHistoryDetail.Builder()
                    .withDdpInstanceId(ddpInstanceDto.getDdpInstanceId())
                    .withMedicalRecordId(medicalRecordId)
                    .withFacility("Office")
                    .withDestructionPolicy("12")
                    .withChangedBy(TEST_USER);

            recId1 = MedicalRecordTestUtil.createOncHistoryDetail(ddpParticipantId, builder.build(), esIndex);

            builder.withDestructionPolicy("3");
            recId2 = MedicalRecordTestUtil.createOncHistoryDetail(ddpParticipantId, builder.build(), esIndex);

            builder.withFacility("Other office");
            recId3 = MedicalRecordTestUtil.createOncHistoryDetail(ddpParticipantId, builder.build(), esIndex);

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
                MedicalRecordTestUtil.deleteOncHistoryDetail(recId1);
            }
            if (recId2 != -1) {
                MedicalRecordTestUtil.deleteOncHistoryDetail(recId2);
            }
            if (recId3 != -1) {
                MedicalRecordTestUtil.deleteOncHistoryDetail(recId3);
            }
            MedicalRecordTestUtil.deleteMedicalRecord(medicalRecordId);

            // ptp2
            if (ptp2MedicalRecordId != -1) {
                MedicalRecordTestUtil.deleteMedicalRecord(ptp2MedicalRecordId);
            }
            if (ptp2 != null) {
                TestParticipantUtil.deleteParticipant(ptp2.getParticipantId().orElseThrow());
            }
        }
    }
}
