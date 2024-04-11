package org.broadinstitute.dsm.db;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDaoImpl;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDto;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
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
    private static final ElasticSearchService elasticSearchService = new ElasticSearchService();
    private static final String TEST_USER = "TEST_USER";
    private static DDPInstanceDto ddpInstanceDto;
    private static String instanceName;
    private static String esIndex;
    private static final List<ParticipantDto> participants = new ArrayList<>();
    private static MedicalRecordTestUtil medicalRecordTestUtil;

    @BeforeClass
    public static void setup() throws Exception {
        instanceName = "onchistorydetailtest";
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/lmsMappings.json", null);
        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
        medicalRecordTestUtil = new MedicalRecordTestUtil();
    }

    @AfterClass
    public static void tearDown() {
        DdpInstanceGroupTestUtil.deleteInstance(ddpInstanceDto);
        ElasticTestUtil.deleteIndex(esIndex);
    }

    @After
    public void deleteParticipants() {
        medicalRecordTestUtil.tearDown();
        participants.forEach(participantDto ->
                TestParticipantUtil.deleteParticipant(participantDto.getRequiredParticipantId()));
    }

    @Test
    public void updateDestructionPolicyTest() {
        ParticipantDto ptp1 = TestParticipantUtil.createParticipantWithEsProfile("ptp1_onchistory",
                ddpInstanceDto, esIndex);
        participants.add(ptp1);
        String ddpParticipantId = ptp1.getRequiredDdpParticipantId();
        int medicalRecordId = medicalRecordTestUtil.createMedicalRecord(ptp1, ddpInstanceDto);

        log.debug("ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));

        OncHistoryDetailDaoImpl oncHistoryDetailDao = new OncHistoryDetailDaoImpl();
        try {
            // create another ptp with a medical record but no onc history
            ParticipantDto ptp2 = TestParticipantUtil.createParticipantWithEsProfile("ptp2_onchistory",
                    ddpInstanceDto, esIndex);
            participants.add(ptp2);
            String ddpParticipantId2 = ptp2.getRequiredDdpParticipantId();

            log.debug("ES participant record for {}: {}", ddpParticipantId2,
                    ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId2));
            medicalRecordTestUtil.createMedicalRecord(ptp2, ddpInstanceDto);

            // add some onc history detail records
            OncHistoryDetail.Builder builder = new OncHistoryDetail.Builder()
                    .withDdpInstanceId(ddpInstanceDto.getDdpInstanceId())
                    .withMedicalRecordId(medicalRecordId)
                    .withFacility("Office")
                    .withDestructionPolicy("12")
                    .withChangedBy(TEST_USER);

            int recId1 = medicalRecordTestUtil.createOncHistoryDetail(ptp1, builder.build(), esIndex);

            builder.withDestructionPolicy("3");
            int recId2 = medicalRecordTestUtil.createOncHistoryDetail(ptp1, builder.build(), esIndex);

            builder.withFacility("Other office");
            int recId3 = medicalRecordTestUtil.createOncHistoryDetail(ptp1, builder.build(), esIndex);

            // update and verify
            OncHistoryDetail.updateDestructionPolicy("5", "Office", instanceName, TEST_USER);

            OncHistoryDetailDto updateRec1 = oncHistoryDetailDao.get(recId1).orElseThrow();
            Assert.assertEquals("5", updateRec1.getColumnValues().get("destruction_policy"));
            OncHistoryDetailDto updateRec2 = oncHistoryDetailDao.get(recId2).orElseThrow();
            Assert.assertEquals("5", updateRec2.getColumnValues().get("destruction_policy"));
            OncHistoryDetailDto updateRec3 = oncHistoryDetailDao.get(recId3).orElseThrow();
            Assert.assertEquals("3", updateRec3.getColumnValues().get("destruction_policy"));

            ElasticSearchParticipantDto esParticipant =
                    elasticSearchService.getRequiredParticipantDocument(ddpParticipantId, esIndex);
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
        }
    }
}
