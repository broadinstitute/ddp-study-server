package org.broadinstitute.dsm.phimanifest;

import java.util.List;
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
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.phimanifest.PhiManifest;
import org.broadinstitute.dsm.service.phimanifest.PhiManifestService;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.OncHistoryTestUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PhiManifestTest extends DbAndElasticBaseTest {
    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static final String instanceName = "phi_report_instance";
    private static final String lmsStudyGuid = "cmi-lms";
    private static final String groupName = "phi_report_group";
    static OncHistoryTestUtil lmsOncHistoryTestUtil;
    private static String lmsEsIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private PhiManifestService phiManifestService = new PhiManifestService();
    static String userEmail = "phiReportEligibleTestUser@unittest.dev";
    static String eligibleParticipantGuid1 = "PHI_REP_0_PARTICIPANT";
    static String unEligibleParticipantGuid1 = "PHI_REP_1_PARTICIPANT";
    static String childReportGuid = "CHILD_1_PARTICIPANT";
    String eligibleTestOrderId = "ELIGIBLE_ORDER";
    String notEligibleTestOrderId = "Not-ELIGIBLE_ORDER";
    String childReportOrderId = "CHILD-ELIGIBLE_ORDER";
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


    @Test
    public void phiReportTest() throws Exception {
        String pdo = "child-report-PDO";
        String smIdValue = "child-SM-ID";
        ParticipantDto participantDto = lmsOncHistoryTestUtil.createSharedLearningParticipant(childReportGuid, ddpInstanceDto,
                "2020-10-10");
        childReportGuid = participantDto.getDdpParticipantIdOrThrow();
        Map<String, Object> response = (Map<String, Object>) lmsOncHistoryTestUtil.createSmId(participantDto, smIdValue, ddpInstanceDto);
        int smIdPk = (int) response.get("smIdPk");
        SmId smId = new TissueSMIDDao().getBySmIdPk(smIdPk);
        MercuryOrderDto eligibleMercuryOrder = new MercuryOrderDto.Builder().withMercuryPdoId(pdo).withOrderId(childReportOrderId)
                .withDdpInstanceId(ddpInstanceDto.getDdpInstanceId()).withTissueId(smId.getTissueId())
                .withCreatedByUserId(lmsOncHistoryTestUtil.getUserId()).withOrderDate(System.currentTimeMillis()).withDsmKitRequestId(null)
                .withDdpParticipantId(childReportGuid).withOrderStatus("Submitted").withStatusDate(System.currentTimeMillis())
                .withBarcode(smIdValue).build();
        int mercuryOrderId = mercuryOrderDao.create(eligibleMercuryOrder, "");

        Tissue createdTissue = new TissueDao().get(smId.getTissueId()).get();
        OncHistoryDetail oncHistoryDetail = new OncHistoryDetail().getOncHistoryDetail(createdTissue.getOncHistoryDetailId(), instanceName);
        lmsOncHistoryTestUtil.createPatchRequest(childReportGuid, participantDto.getParticipantId().get(), instanceName, userEmail,
                "oD.accessionNumber", "ACCESSION-NUMBER", "oD", "ddpParticipantId",
                oncHistoryDetail.getOncHistoryDetailId(), childReportGuid);
        lmsOncHistoryTestUtil.createPatchRequest(childReportGuid, participantDto.getParticipantId().get(), instanceName, userEmail,
                "oD.histology", "some-histology", "oD", "ddpParticipantId",
                oncHistoryDetail.getOncHistoryDetailId(), childReportGuid);
        lmsOncHistoryTestUtil.createPatchRequest(childReportGuid, participantDto.getParticipantId().get(), instanceName, userEmail,
                "oD.facility", "some-hospital", "oD", "ddpParticipantId",
                oncHistoryDetail.getOncHistoryDetailId(), childReportGuid);
        lmsOncHistoryTestUtil.createPatchRequest(childReportGuid, participantDto.getParticipantId().get(), instanceName, userEmail,
                "t.collaboratorSampleId", "some-collaborator-sample-id", "t", "oncHisotryDetail",
                createdTissue.getTissueId(), String.valueOf(oncHistoryDetail.getOncHistoryDetailId()));
        lmsOncHistoryTestUtil.createPatchRequest(childReportGuid, participantDto.getParticipantId().get(), instanceName, userEmail,
                "t.blockIdShl", "some-block-id", "t", "oncHisotryDetail", createdTissue.getTissueId(),
                String.valueOf(oncHistoryDetail.getOncHistoryDetailId()));
        lmsOncHistoryTestUtil.createPatchRequest(childReportGuid, participantDto.getParticipantId().get(), instanceName, userEmail,
                "t.tissueSite", "some-tissue-site", "t", "oncHisotryDetail", createdTissue.getTissueId(),
                String.valueOf(oncHistoryDetail.getOncHistoryDetailId()));
        lmsOncHistoryTestUtil.createPatchRequest(childReportGuid, participantDto.getParticipantId().get(), instanceName, userEmail,
                "t.tissueSequence", "some-tissue-sequence", "t", "oncHisotryDetail",
                createdTissue.getTissueId(), String.valueOf(oncHistoryDetail.getOncHistoryDetailId()));
        ElasticSearchParticipantDto participant = ElasticSearchUtil.getParticipantESDataByParticipantId(
                ddpInstanceDto.getEsParticipantIndex(), childReportGuid);
        List<MercuryOrderDto> orders = new MercuryOrderDao().getByOrderId(childReportOrderId);
        PhiManifest phiManifest = phiManifestService.generateDataForReport(participant, orders, ddpInstanceDto);
        assertPhiManifest(phiManifest, participantDto, orders, createdTissue, oncHistoryDetail, participant);
        mercuryOrderDao.delete(mercuryOrderId);
        lmsOncHistoryTestUtil.deleteOncHistory(childReportGuid, participantDto.getParticipantId().get(), instanceName, userEmail,
                oncHistoryDetail.getOncHistoryDetailId());
    }

    private void assertPhiManifest(PhiManifest phiManifest, ParticipantDto participantDto, List<MercuryOrderDto> orders, Tissue tissue,
                                   OncHistoryDetail oncHistoryDetail, ElasticSearchParticipantDto participant) {
        MercuryOrderDto mercuryOrderDto = orders.get(0);
        Assert.assertEquals(mercuryOrderDto.getOrderId(), phiManifest.getClinicalOrderId());
        Assert.assertEquals(mercuryOrderDto.getMercuryPdoId(), phiManifest.getClinicalPdoNumber());
        Assert.assertEquals(phiManifestService.getDateFromEpoch(mercuryOrderDto.getOrderDate()), phiManifest.getClinicalOrderDate());
        Assert.assertEquals(participant.getProfile().get().getFirstName(), phiManifest.getFirstName());
        Assert.assertEquals(participant.getProfile().get().getLastName(), phiManifest.getLastName());
        Assert.assertEquals(participant.getProfile().get().getHruid(), phiManifest.getShortId());
        Assert.assertEquals(oncHistoryDetail.getAccessionNumber(), phiManifest.getAccessionNumber());
        Assert.assertEquals(oncHistoryDetail.getDatePx(), phiManifest.getDateOfPx());
        Assert.assertEquals(oncHistoryDetail.getHistology(), phiManifest.getHistology());
        Assert.assertEquals(oncHistoryDetail.getFacility(), phiManifest.getFacility());
        Assert.assertEquals(tissue.getCollaboratorSampleId(), phiManifest.getTumorCollaboratorSampleId());
        Assert.assertEquals(tissue.getBlockIdShl(), phiManifest.getBlockId());
        Assert.assertEquals(tissue.getTissueSite(), phiManifest.getTissueSite());
        Assert.assertEquals(tissue.getTissueSequence(), phiManifest.getSequencingResults());
    }
}
