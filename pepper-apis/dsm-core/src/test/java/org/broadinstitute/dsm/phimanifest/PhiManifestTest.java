package org.broadinstitute.dsm.phimanifest;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
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
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.phimanifest.PhiManifest;
import org.broadinstitute.dsm.service.phimanifest.PhiManifestService;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.DateTimeUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.OncHistoryTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.broadinstitute.dsm.service.phimanifest.PhiManifestService.*;

public class PhiManifestTest extends DbAndElasticBaseTest {
    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static final String instanceName = "phi_report_instance";
    private static final String lmsStudyGuid = DBConstants.LMS_STUDY_GUID;
    private static final String groupName = "phi_report_group";
    static OncHistoryTestUtil lmsOncHistoryTestUtil;
    private static String lmsEsIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private PhiManifestService phiManifestService = new PhiManifestService();
    static String userEmail = "phiReportEligibleTestUser@unittest.dev";
    static String adminUserEmail = "adminUserPhiReport@unittest.dev";
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
        lmsOncHistoryTestUtil = new OncHistoryTestUtil(instanceName, lmsStudyGuid, userEmail, adminUserEmail,
                groupName, "lmsPrefix", lmsEsIndex);
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
        Map<String, Object> response = lmsOncHistoryTestUtil.createSmId(participantDto, eligibleSmId, ddpInstanceDto);
        int smIdPk = (int) response.get("smIdPk");
        SmId smId = new TissueSMIDDao().getBySmIdPk(smIdPk);
        MercuryOrderDto eligibleMercuryOrder = new MercuryOrderDto.Builder().withMercuryPdoId(eligiblePDO).withOrderId(eligibleTestOrderId)
                        .withDdpInstanceId(ddpInstanceDto.getDdpInstanceId()).withTissueId(smId.getTissueId())
                .withDsmKitRequestId(null).withDdpParticipantId(eligibleParticipantGuid1).withBarcode(eligibleSmId).build();
        int mercuryOrderId = mercuryOrderDao.create(eligibleMercuryOrder, "");
        List<MercuryOrderDto> orders = mercuryOrderDao.getByOrderId(eligibleTestOrderId);
        Assert.assertFalse(orders.stream().anyMatch(order -> !order.orderMatchesParticipantAndStudyInfo(eligibleParticipantGuid1,
                ddpInstanceDto)));
        mercuryOrderDao.delete(mercuryOrderId);
        Tissue createdTissue = new TissueDao().get(smId.getTissueId()).get();
        OncHistoryDetail oncHistoryDetail = OncHistoryDetail.getOncHistoryDetail(createdTissue.getOncHistoryDetailId(), instanceName);
        lmsOncHistoryTestUtil.deleteOncHistory(eligibleParticipantGuid1, participantDto.getParticipantId().get(), instanceName, userEmail,
                oncHistoryDetail.getOncHistoryDetailId());
    }

    @Test
    public void isSequencingOrderNotValidTest() throws Exception {
        String pdo = "ANOTHER-PDO-VALUE";
        String smIdValue = "not-the-same-barcode";
        ParticipantDto participantDto = lmsOncHistoryTestUtil.createParticipant(unEligibleParticipantGuid1, ddpInstanceDto);
        unEligibleParticipantGuid1 = participantDto.getDdpParticipantIdOrThrow();
        Map<String, Object> response = lmsOncHistoryTestUtil.createSmId(participantDto, smIdValue, ddpInstanceDto);
        int smIdPk = (int) response.get("smIdPk");
        SmId smId = new TissueSMIDDao().getBySmIdPk(smIdPk);
        MercuryOrderDto eligibleMercuryOrder = new MercuryOrderDto.Builder().withMercuryPdoId(pdo).withOrderId(notEligibleTestOrderId)
                .withDdpInstanceId(ddpInstanceDto.getDdpInstanceId()).withTissueId(smId.getTissueId())
                .withDsmKitRequestId(null).withDdpParticipantId(eligibleParticipantGuid1).withBarcode(smIdValue).build();
        int mercuryOrderId = mercuryOrderDao.create(eligibleMercuryOrder, "");
        List<MercuryOrderDto> orders = mercuryOrderDao.getByOrderId(notEligibleTestOrderId);
        Assert.assertTrue(orders.stream().anyMatch(order -> !order.orderMatchesParticipantAndStudyInfo(unEligibleParticipantGuid1,
                ddpInstanceDto)));
        mercuryOrderDao.delete(mercuryOrderId);
        Tissue createdTissue = new TissueDao().get(smId.getTissueId()).get();
        OncHistoryDetail oncHistoryDetail = OncHistoryDetail.getOncHistoryDetail(createdTissue.getOncHistoryDetailId(), instanceName);
        lmsOncHistoryTestUtil.deleteOncHistory(unEligibleParticipantGuid1, participantDto.getParticipantId().get(), instanceName, userEmail,
                oncHistoryDetail.getOncHistoryDetailId());
    }

    @Test
    public void testLMSAdultParticipantEligibility() {
        String eligibleGuid = "adultEligibleGuid";
        ParticipantDto adultEligibleParticipant = TestParticipantUtil.createSharedLearningParticipant(eligibleGuid, ddpInstanceDto,
                "1990-10-01", null, lmsEsIndex);
        lmsOncHistoryTestUtil.getParticipantIds().add(adultEligibleParticipant.getParticipantId().orElseThrow());
        ElasticSearchParticipantDto adultEligibleParticipantDto = ElasticSearchUtil.getParticipantESDataByParticipantId(
                ddpInstanceDto.getEsParticipantIndex(), adultEligibleParticipant.getDdpParticipantId().orElseThrow());
        lmsOncHistoryTestUtil.getParticipantIds().add(adultEligibleParticipant.getParticipantId().orElseThrow());
        Assert.assertTrue(phiManifestService.isParticipantConsented(adultEligibleParticipantDto, ddpInstanceDto));

        String unEligibleGuid = "unEligibleGuid";
        ParticipantDto ineligibleAdult = TestParticipantUtil.createIneligibleSharedLearningParticipant(unEligibleGuid, ddpInstanceDto,
                "1990-11-11", lmsEsIndex);
        ElasticSearchParticipantDto ineligibleAdultDto = ElasticSearchUtil.getParticipantESDataByParticipantId(
                ddpInstanceDto.getEsParticipantIndex(), ineligibleAdult.getDdpParticipantId().orElseThrow());

        lmsOncHistoryTestUtil.getParticipantIds().add(ineligibleAdult.getParticipantId().orElseThrow());
        Assert.assertFalse(phiManifestService.isParticipantConsented(ineligibleAdultDto, ddpInstanceDto));

        // now change activity status for pediatric and adult consent and verify that
        // the participants aren't considered consented if their consent status isn't complete.
        Assert.assertTrue(phiManifestService.isParticipantConsented(adultEligibleParticipantDto, ddpInstanceDto));
        if (!adultEligibleParticipantDto.changeActivityStatus(CONSENT_ADDENDUM_ACTIVITY_ACTIVITY_CODE, "BOGUS")) {
            Assert.fail("Could not change status of " + CONSENT_ADDENDUM_ACTIVITY_ACTIVITY_CODE);
        }
        Assert.assertFalse(phiManifestService.isParticipantConsented(adultEligibleParticipantDto, ddpInstanceDto));

    }

    @Test
    public void testLMSPediatricParticipantEligibility() {
        String childParticipantGuid = "childEligibleGuid";
        ParticipantDto childParticipant = TestParticipantUtil.createSharedLearningParticipant(childParticipantGuid, ddpInstanceDto,
                "2020-11-11", "2038-11-11", lmsEsIndex); // todo arz make birth dates relative to now
        lmsOncHistoryTestUtil.getParticipantIds().add(childParticipant.getParticipantId().orElseThrow());
        ElasticSearchParticipantDto childParticipantDto = ElasticSearchUtil.getParticipantESDataByParticipantId(
                ddpInstanceDto.getEsParticipantIndex(), childParticipant.getDdpParticipantId().orElseThrow());
        Assert.assertTrue(phiManifestService.isParticipantConsented(childParticipantDto, ddpInstanceDto));
        if (!childParticipantDto.changeActivityStatus(CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_CODE, "FAKE")) {
            Assert.fail("Could not change status of " + CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_CODE);
        }
        Assert.assertFalse(phiManifestService.isParticipantConsented(childParticipantDto, ddpInstanceDto));
    }

    @Test
    public void phiReportTest() throws Exception {
        String pdo = "child-report-PDO";
        String smIdValue = "child-SM-ID";
        ParticipantDto participantDto = TestParticipantUtil.createSharedLearningParticipant(childReportGuid, ddpInstanceDto,
                "2020-10-10", null, lmsEsIndex);
        String initialStudyGuid = ddpInstanceDto.getStudyGuid();
        childReportGuid = participantDto.getDdpParticipantIdOrThrow();
        lmsOncHistoryTestUtil.getParticipantIds().add(participantDto.getParticipantId().orElseThrow());
        Map<String, Object> response = lmsOncHistoryTestUtil.createSmId(participantDto, smIdValue, ddpInstanceDto);
        int smIdPk = (int) response.get("smIdPk");
        SmId smId = new TissueSMIDDao().getBySmIdPk(smIdPk);
        Tissue createdTissue = new TissueDao().get(smId.getTissueId()).get();
        OncHistoryDetail oncHistoryDetail = OncHistoryDetail.getOncHistoryDetail(createdTissue.getOncHistoryDetailId(), instanceName);
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
        MercuryOrderDto eligibleMercuryOrder = new MercuryOrderDto.Builder().withMercuryPdoId(pdo).withOrderId(childReportOrderId)
                .withDdpInstanceId(ddpInstanceDto.getDdpInstanceId()).withTissueId(smId.getTissueId())
                .withCreatedByUserId(lmsOncHistoryTestUtil.getUserId()).withOrderDate(System.currentTimeMillis()).withDsmKitRequestId(null)
                .withDdpParticipantId(childReportGuid).withOrderStatus("Submitted").withStatusDate(System.currentTimeMillis())
                .withBarcode(smIdValue).build();
        int mercuryOrderId = mercuryOrderDao.create(eligibleMercuryOrder, "");
        ElasticSearchParticipantDto participant = ElasticSearchUtil.getParticipantESDataByParticipantId(
                ddpInstanceDto.getEsParticipantIndex(), childReportGuid);
        List<MercuryOrderDto> orders = new MercuryOrderDao().getByOrderId(childReportOrderId);
        createdTissue = new TissueDao().get(smId.getTissueId()).get();
        PhiManifest phiManifest = phiManifestService.generateDataForReport(participant, orders, ddpInstanceDto);
        oncHistoryDetail = OncHistoryDetail.getOncHistoryDetail(createdTissue.getOncHistoryDetailId(), instanceName);
        assertPhiManifest(phiManifest, orders, createdTissue, oncHistoryDetail, participant);

        // now change the answers to various questions and verify that the phi manifest columns are correct
        participant.changeQuestionAnswer(CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_CODE,
                SOMATIC_CONSENT_TUMOR_PEDIATRIC_QUESTION_STABLE_ID, "");
        phiManifest = phiManifestService.generateDataForReport(participant, orders, ddpInstanceDto);
        assertPhiManifest(phiManifest, orders, createdTissue, oncHistoryDetail, participant);
        participant.changeQuestionAnswer(CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_CODE,
                SOMATIC_CONSENT_TUMOR_PEDIATRIC_QUESTION_STABLE_ID, false);
        participant.changeQuestionAnswer(CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_CODE, SOMATIC_ASSENT_ADDENDUM_QUESTION_STABLE_ID, "");
        phiManifest = phiManifestService.generateDataForReport(participant, orders, ddpInstanceDto);
        assertPhiManifest(phiManifest, orders, createdTissue, oncHistoryDetail, participant);

        participant.changeQuestionAnswer(CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_CODE, SOMATIC_ASSENT_ADDENDUM_QUESTION_STABLE_ID, null);
        phiManifest = phiManifestService.generateDataForReport(participant, orders, ddpInstanceDto);
        assertPhiManifest(phiManifest, orders, createdTissue, oncHistoryDetail, participant);

        participant.changeQuestionAnswer(CONSENT_ADDENDUM_ACTIVITY_ACTIVITY_CODE,
                OS2_QUESTION_SOMATIC_CONSENT_ADDENDUM_TUMOR_STABLE_ID, null);
        phiManifest = phiManifestService.generateDataForReport(participant, orders, ddpInstanceDto);
        assertPhiManifest(phiManifest, orders, createdTissue, oncHistoryDetail, participant);

        // change the LMS consent tumor to blank/true/false and verify
        participant.changeQuestionAnswer(CONSENT_ADDENDUM_ACTIVITY_ACTIVITY_CODE,
                LMS_QUESTION_SOMATIC_CONSENT_ADDENDUM_TUMOR_STABLE_ID, null);
        phiManifest = phiManifestService.generateDataForReport(participant, orders, ddpInstanceDto);
        assertPhiManifest(phiManifest, orders, createdTissue, oncHistoryDetail, participant);
        Assert.assertEquals("", phiManifest.getSomaticConsentTumorResponse());

        participant.changeQuestionAnswer(CONSENT_ADDENDUM_ACTIVITY_ACTIVITY_CODE,
                LMS_QUESTION_SOMATIC_CONSENT_ADDENDUM_TUMOR_STABLE_ID, true);
        phiManifest = phiManifestService.generateDataForReport(participant, orders, ddpInstanceDto);
        assertPhiManifest(phiManifest, orders, createdTissue, oncHistoryDetail, participant);
        Assert.assertEquals("Yes", phiManifest.getSomaticConsentTumorResponse());

        participant.changeQuestionAnswer(CONSENT_ADDENDUM_ACTIVITY_ACTIVITY_CODE,
                LMS_QUESTION_SOMATIC_CONSENT_ADDENDUM_TUMOR_STABLE_ID, false);
        phiManifest = phiManifestService.generateDataForReport(participant, orders, ddpInstanceDto);
        assertPhiManifest(phiManifest, orders, createdTissue, oncHistoryDetail, participant);
        Assert.assertEquals("No", phiManifest.getSomaticConsentTumorResponse());

        try {
            // verify that consent tumor response strings in the report are correct
            // for adult consent in both OS.

            ddpInstanceDto.setStudyGuid(DBConstants.OSTEO_STUDY_GUID);

            if (!participant.changeQuestionAnswer(CONSENT_ADDENDUM_ACTIVITY_ACTIVITY_CODE,
                    OS2_QUESTION_SOMATIC_CONSENT_ADDENDUM_TUMOR_STABLE_ID, true)) {
                Assert.fail(String.format("%s.%s is missing from the participant's activities.  Is the test json correct?",
                        CONSENT_ADDENDUM_ACTIVITY_ACTIVITY_CODE,
                        OS2_QUESTION_SOMATIC_CONSENT_ADDENDUM_TUMOR_STABLE_ID));
            }

            phiManifest = phiManifestService.generateDataForReport(participant, orders, ddpInstanceDto);
            assertPhiManifest(phiManifest, orders, createdTissue, oncHistoryDetail, participant);
            Assert.assertEquals("Yes", phiManifest.getSomaticConsentTumorResponse());

            participant.changeQuestionAnswer(CONSENT_ADDENDUM_ACTIVITY_ACTIVITY_CODE,
                    OS2_QUESTION_SOMATIC_CONSENT_ADDENDUM_TUMOR_STABLE_ID, false);
            phiManifest = phiManifestService.generateDataForReport(participant, orders, ddpInstanceDto);
            assertPhiManifest(phiManifest, orders, createdTissue, oncHistoryDetail, participant);
            Assert.assertEquals("No", phiManifest.getSomaticConsentTumorResponse());

            participant.changeQuestionAnswer(CONSENT_ADDENDUM_ACTIVITY_ACTIVITY_CODE,
                    OS2_QUESTION_SOMATIC_CONSENT_ADDENDUM_TUMOR_STABLE_ID, true);
            phiManifest = phiManifestService.generateDataForReport(participant, orders, ddpInstanceDto);
            assertPhiManifest(phiManifest, orders, createdTissue, oncHistoryDetail, participant);
            Assert.assertEquals("Yes", phiManifest.getSomaticConsentTumorResponse());

            participant.changeQuestionAnswer(CONSENT_ADDENDUM_ACTIVITY_ACTIVITY_CODE,
                    OS2_QUESTION_SOMATIC_CONSENT_ADDENDUM_TUMOR_STABLE_ID, null);
            phiManifest = phiManifestService.generateDataForReport(participant, orders, ddpInstanceDto);
            assertPhiManifest(phiManifest, orders, createdTissue, oncHistoryDetail, participant);
            Assert.assertEquals("", phiManifest.getSomaticConsentTumorResponse());

        } finally {
            // if there's an issue, always reset the study guid to LMS
            ddpInstanceDto.setStudyGuid(initialStudyGuid);
        }

        mercuryOrderDao.delete(mercuryOrderId);
        lmsOncHistoryTestUtil.deleteOncHistory(childReportGuid, participantDto.getParticipantId().get(), instanceName, userEmail,
                oncHistoryDetail.getOncHistoryDetailId());
    }

    private void assertPhiManifest(PhiManifest phiManifest, List<MercuryOrderDto> orders, Tissue tissue,
                                   OncHistoryDetail oncHistoryDetail, ElasticSearchParticipantDto participant) {
        MercuryOrderDto mercuryOrderDto = orders.get(0);
        Assert.assertEquals(mercuryOrderDto.getOrderId(), phiManifest.getClinicalOrderId());
        Assert.assertEquals(mercuryOrderDto.getMercuryPdoId(), phiManifest.getClinicalPdoNumber());
        Assert.assertEquals(DateTimeUtil.getDateFromEpoch(mercuryOrderDto.getOrderDate()), phiManifest.getClinicalOrderDate());
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
        Profile participantProfile = participant.getProfile().get();
        Assert.assertEquals(participantProfile.getHruid(), phiManifest.getShortId());
        Assert.assertEquals(participantProfile.getFirstName(), phiManifest.getFirstName());
        Assert.assertEquals(participantProfile.getLastName(), phiManifest.getLastName());
        Assert.assertEquals(participant.getDsm().get().getDateOfBirth(), phiManifest.getDateOfBirth());

        String somaticTumorReportValue = PhiManifestService.convertBooleanActivityAnswerToString(
                participant.getParticipantAnswerInSurvey(CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_CODE,
                        SOMATIC_CONSENT_TUMOR_PEDIATRIC_QUESTION_STABLE_ID));
        Assert.assertTrue(
                StringUtils.equals(somaticTumorReportValue, phiManifest.getSomaticConsentTumorPediatricResponse()));

        String somaticAssentAddendumReportValue = PhiManifestService.convertBooleanActivityAnswerToString(
                participant.getParticipantAnswerInSurvey(CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_CODE,
                        SOMATIC_ASSENT_ADDENDUM_QUESTION_STABLE_ID));
        Assert.assertTrue(
                StringUtils.equals(somaticAssentAddendumReportValue, phiManifest.getSomaticAssentAddendumResponse()));

        String os2SomaticConsent = PhiManifestService.convertBooleanActivityAnswerToString(
                participant.getParticipantAnswerInSurvey(CONSENT_ADDENDUM_ACTIVITY_ACTIVITY_CODE,
                        OS2_QUESTION_SOMATIC_CONSENT_ADDENDUM_TUMOR_STABLE_ID));

        String lmsConsentTumor = PhiManifestService.convertBooleanActivityAnswerToString(
                participant.getParticipantAnswerInSurvey(CONSENT_ADDENDUM_ACTIVITY_ACTIVITY_CODE,
                        LMS_QUESTION_SOMATIC_CONSENT_ADDENDUM_TUMOR_STABLE_ID));

        boolean lmsOrOsConsentForTumorTrue =
                StringUtils.equals(lmsConsentTumor, phiManifest.getSomaticConsentTumorResponse())
                || StringUtils.equals(os2SomaticConsent, phiManifest.getSomaticConsentTumorResponse());

        Assert.assertTrue(lmsOrOsConsentForTumorTrue);
    }
}
