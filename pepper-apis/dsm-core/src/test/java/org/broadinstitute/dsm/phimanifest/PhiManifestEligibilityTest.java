package org.broadinstitute.dsm.phimanifest;

import java.util.Map;

import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.SmId;
import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueDao;
import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueSMIDDao;
import org.broadinstitute.dsm.db.dao.mercury.MercuryOrderDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.mercury.MercuryOrderDto;
import org.broadinstitute.dsm.service.phimanifest.PhiManifestService;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.OncHistoryTestUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PhiManifestEligibilityTest extends DbAndElasticBaseTest {
    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static final String instanceName = "phi_report_instance";
    private static final String lmsStudyGuid = "cmi-lms";
    private static final String groupName = "phi_report_group";
    static OncHistoryTestUtil lmsOncHistoryTestUtil;
    private static String lmsEsIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private PhiManifestService phiManifestService = new PhiManifestService();
    static String userEmail = "phiReportTestUser@unittest.dev";
    static String eligibleParticipantGuid1 = "PHI_REP_0_PARTICIPANT";
    static String unEligibleParticipantGuid1 = "PHI_REP_1_PARTICIPANT";
    String eligibleTestOrderId = "ELIGIBLE_ORDER";
    String notEligibleTestOrderId = "Not-ELIGIBLE_ORDER";
    String mercuryOrderId = "";
    MercuryOrderDao mercuryOrderDao = new MercuryOrderDao();

    @BeforeClass
    public static void doFirst() {
        lmsEsIndex = ElasticTestUtil.createIndex(instanceName, "elastic/lmsMappings.json", null);
        lmsOncHistoryTestUtil = new OncHistoryTestUtil(instanceName, lmsStudyGuid, userEmail, groupName, "lmsPrefix", lmsEsIndex);
        lmsOncHistoryTestUtil.initialize();
        ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(instanceName).orElseThrow();
    }

    @AfterClass
    public static void cleanUpAfter() {
        lmsOncHistoryTestUtil.deleteEverything();
        try {
            ddpInstanceDao.delete(ddpInstanceDto.getDdpInstanceId());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception: " + e);
        }
        ElasticTestUtil.deleteIndex(lmsEsIndex);
    }
    
    @Test
    public void isSequencingOrderValidTest() throws Exception {
        String eligiblePDO = "PDO-VALUE";
        String eligibleSmId = "some-barcode";
        ParticipantDto participantDto = lmsOncHistoryTestUtil.createParticipant(eligibleParticipantGuid1, ddpInstanceDto);
        eligibleParticipantGuid1 = participantDto.getDdpParticipantIdOrThrow();
        Map<String, Object> response =
                (Map<String, Object>) lmsOncHistoryTestUtil.createSmId(participantDto, eligibleSmId, ddpInstanceDto);
        int smIdPk = (int) response.get("smIdPk");
        SmId smId = new TissueSMIDDao().getBySmIdPk(smIdPk);
        MercuryOrderDto eligibleMercuryOrder = new MercuryOrderDto.Builder().withMercuryPdoId(eligiblePDO).withOrderId(eligibleTestOrderId)
                        .withDdpInstanceId(ddpInstanceDto.getDdpInstanceId()).withTissueId(smId.getTissueId())
                .withDsmKitRequestId(null).withDdpParticipantId(eligibleParticipantGuid1).withBarcode(eligibleSmId).build();
        int mercuryOrderId = mercuryOrderDao.create(eligibleMercuryOrder, "");
        Assert.assertTrue(phiManifestService.isSequencingOrderValid(eligibleTestOrderId, eligibleParticipantGuid1, ddpInstanceDto));
        mercuryOrderDao.delete(mercuryOrderId);
        Tissue createdTissue = new TissueDao().get(smId.getTissueId()).get();
        OncHistoryDetail oncHistoryDetail = new OncHistoryDetail().getOncHistoryDetail(createdTissue.getOncHistoryDetailId(), instanceName);
        lmsOncHistoryTestUtil.deleteOncHistory(eligibleParticipantGuid1, participantDto.getParticipantId().get(), instanceName, userEmail,
                oncHistoryDetail.getOncHistoryDetailId());
    }

    @Test
    public void isSequencingOrderNotValidTest() throws Exception {
        String pdo = "ANOTHER-PDO-VALUE";
        String smIdValue = "not-the-same-barcode";
        ParticipantDto participantDto = lmsOncHistoryTestUtil.createParticipant(unEligibleParticipantGuid1, ddpInstanceDto);
        unEligibleParticipantGuid1 = participantDto.getDdpParticipantIdOrThrow();
        Map<String, Object> response =
                (Map<String, Object>) lmsOncHistoryTestUtil.createSmId(participantDto, smIdValue, ddpInstanceDto);
        int smIdPk = (int) response.get("smIdPk");
        SmId smId = new TissueSMIDDao().getBySmIdPk(smIdPk);
        MercuryOrderDto eligibleMercuryOrder = new MercuryOrderDto.Builder().withMercuryPdoId(pdo).withOrderId(notEligibleTestOrderId)
                .withDdpInstanceId(ddpInstanceDto.getDdpInstanceId()).withTissueId(smId.getTissueId())
                .withDsmKitRequestId(null).withDdpParticipantId(eligibleParticipantGuid1).withBarcode(smIdValue).build();
        int mercuryOrderId = mercuryOrderDao.create(eligibleMercuryOrder, "");
        Assert.assertFalse(phiManifestService.isSequencingOrderValid(notEligibleTestOrderId, unEligibleParticipantGuid1, ddpInstanceDto));
        mercuryOrderDao.delete(mercuryOrderId);
        Tissue createdTissue = new TissueDao().get(smId.getTissueId()).get();
        OncHistoryDetail oncHistoryDetail = new OncHistoryDetail().getOncHistoryDetail(createdTissue.getOncHistoryDetailId(), instanceName);
        lmsOncHistoryTestUtil.deleteOncHistory(unEligibleParticipantGuid1, participantDto.getParticipantId().get(), instanceName, userEmail,
                oncHistoryDetail.getOncHistoryDetailId());
    }

    @Test
    public void lmsParticipantEligibilityTest() {
        String eligibleGuid = "adultEligibleGuid";
        ParticipantDto adultEligibleParticipant = lmsOncHistoryTestUtil.createSharedLearningParticipant(eligibleGuid, ddpInstanceDto,
                "1990-10-01");
        Assert.assertTrue(phiManifestService.isParticipantConsented(adultEligibleParticipant.getDdpParticipantIdOrThrow(), ddpInstanceDto));
        String unEligibleGuid = "unEligibleGuid";
        //TODO add test for uneligible adult and eligible child
    }
    
}
