package org.broadinstitute.dsm.service.phimanifest;

import java.util.List;
import java.util.Optional;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueDao;
import org.broadinstitute.dsm.db.dao.mercury.MercuryOrderDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.mercury.MercuryOrderDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.phimanifest.PhiManifest;
import org.broadinstitute.dsm.route.phimanifest.PhiManifestReportRoute;
import org.broadinstitute.dsm.route.phimanifest.PhiManifestReportRoute.PhiManifestResponse;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.DateTimeUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

/**
 * Extracts various data fields for a participant and writes them
 * into a "report" that is ultimately displayed for CRCs in PE-CGS
 * studies so that they can more easily view all the data in one
 * place when double checking results that will be shared
 * with a participant.
 */
@Slf4j
public class PhiManifestService {
    public static final String CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_CODE = "CONSENT_ADDENDUM_PEDIATRIC";
    public static final String SOMATIC_CONSENT_TUMOR_PEDIATRIC_QUESTION_STABLE_ID = "SOMATIC_CONSENT_TUMOR_PEDIATRIC";
    public static final String SOMATIC_ASSENT_ADDENDUM_QUESTION_STABLE_ID = "SOMATIC_ASSENT_ADDENDUM";

    public static final String CONSENT_SUSPENDED = "CONSENT_SUSPENDED";
    public static final String CONSENT_ACTIVITY_CODE = "CONSENT";
    public static final String CONSENT_ADDENDUM_ACTIVITY_ACTIVITY_CODE = "CONSENT_ADDENDUM";
    public static final String LMS_QUESTION_SOMATIC_CONSENT_ADDENDUM_TUMOR_STABLE_ID = "SOMATIC_CONSENT_ADDENDUM_TUMOR";
    public static final String OS2_QUESTION_SOMATIC_CONSENT_ADDENDUM_TUMOR_STABLE_ID = "SOMATIC_CONSENT_TUMOR";

    private static final String NOT_VALID_ORDER_ERROR =
            "Sequencing order number %s does not exist or is not a valid clinical order for this participant";

    private static final String NOT_CONSENTED_ERROR = "Participant %s has not consented to receive shared learnings";

    public PhiManifestReportRoute.PhiManifestResponse generateReport(String ddpParticipantId, String sequencingOrderId,
                                                                     DDPInstanceDto ddpInstanceDto) {
        MercuryOrderDao mercuryOrderDao = new MercuryOrderDao();
        List<MercuryOrderDto> orders = mercuryOrderDao.getByOrderId(sequencingOrderId);
        if (orders.isEmpty()
                || orders.stream().anyMatch(order -> !order.orderMatchesParticipantAndStudyInfo(ddpParticipantId, ddpInstanceDto))) {
            String errorMessage = String.format(NOT_VALID_ORDER_ERROR, sequencingOrderId);
            log.warn(errorMessage);
            return new PhiManifestReportRoute.PhiManifestResponse(errorMessage);
        }
        ElasticSearchParticipantDto participant = ElasticSearchUtil.getParticipantESDataByParticipantId(
                ddpInstanceDto.getEsParticipantIndex(), ddpParticipantId);
        if (!isParticipantConsented(participant, ddpInstanceDto)) {
            String errorMessage = String.format(NOT_CONSENTED_ERROR, ddpParticipantId);
            log.warn(errorMessage);
            return new PhiManifestResponse(errorMessage);
        }
        if (participant.getProfile().isEmpty()) {
            throw new DsmInternalError("There is no profile in ES for participant " + ddpParticipantId);
        }
        PhiManifest finalPhiManifest = generateDataForReport(participant, orders, ddpInstanceDto);
        return finalPhiManifest.toResponseArray(ddpParticipantId, sequencingOrderId);
    }

    /**
     * Creates a PhiManifest from the information in participant and in a clinical order
     */
    public static PhiManifest generateDataForReport(@NonNull ElasticSearchParticipantDto participant, @NonNull List<MercuryOrderDto> orders,
                                                    @NonNull DDPInstanceDto ddpInstanceDto) {
        //This method assumes that each order has at most 1 Tumor and at most 1 Normal sample, which is a correct assumption based on
        // clinical ordering criteria currently in place
        PhiManifest phiManifest = new PhiManifest();
        Profile participantProfile = participant.getProfile().orElseThrow();
        phiManifest.setShortId(participantProfile.getHruid());
        phiManifest.setParticipantId(participantProfile.getGuid());
        phiManifest.setRealm(ddpInstanceDto.getInstanceName());
        phiManifest.setFirstName(participantProfile.getFirstName());
        phiManifest.setLastName(participantProfile.getLastName());
        List<String> proxies = participant.getProxies();
        if (proxies != null && !proxies.isEmpty()) {
            String proxyParticipantId = proxies.get(0);
            ElasticSearchParticipantDto proxyParticipant = ElasticSearchUtil.getParticipantESDataByParticipantId(
                    ddpInstanceDto.getEsUsersIndex(), proxyParticipantId);
            if (proxyParticipant.getProfile().isPresent()) {
                Profile proxyParticipantProfile = proxyParticipant.getProfile().get();
                phiManifest.setProxyFirstName(proxyParticipantProfile.getFirstName());
                phiManifest.setProxyLastName(proxyParticipantProfile.getLastName());
            }
        }
        if (participant.getDsm().isPresent()) {
            phiManifest.setDateOfBirth(participant.getDsm().get().getDateOfBirth());
        } else {
            log.warn("participant {} does not have a DSM object in ES", participant.getParticipantId());
        }
        String participantGenderInDsm = participant.getParticipantGender(ddpInstanceDto.getInstanceName(),
                participant.getParticipantId());
        phiManifest.setGender(participantGenderInDsm);
        Optional<MercuryOrderDto> maybeTumorDataInOrder = orders.stream().filter(mercuryOrderDto -> mercuryOrderDto.getTissueId() != null)
                .findFirst();
        if (maybeTumorDataInOrder.isPresent()) {
            MercuryOrderDto clinicalOrderWithTumor = maybeTumorDataInOrder.get();
            Tissue tissue = new TissueDao().get(clinicalOrderWithTumor.getTissueId().longValue()).orElseThrow(() ->
                    new DsmInternalError("No tissue found with tissue id " + clinicalOrderWithTumor.getTissueId()));
            OncHistoryDetail oncHistoryDetail = OncHistoryDetail.getOncHistoryDetail(tissue.getOncHistoryDetailId(),
                    ddpInstanceDto.getInstanceName());
            phiManifest.setDateOfPx(oncHistoryDetail.getDatePx());
            phiManifest.setFacility(oncHistoryDetail.getFacility());
            phiManifest.setAccessionNumber(oncHistoryDetail.getAccessionNumber());
            phiManifest.setHistology(oncHistoryDetail.getHistology());
            phiManifest.setBlockId(tissue.getBlockIdShl());
            phiManifest.setTumorCollaboratorSampleId(tissue.getCollaboratorSampleId());
            phiManifest.setFirstSmId(tissue.getFirstSmId());
            phiManifest.setTissueSite(tissue.getTissueSite());
            phiManifest.setTissueType(tissue.getTissueType());
            phiManifest.setSequencingResults(tissue.getTissueSequence());
        }
        Optional<MercuryOrderDto> maybeNormalDataInOrder = orders.stream().filter(mercuryOrderDto -> mercuryOrderDto.getDsmKitRequestId()
                != null && mercuryOrderDto.getDsmKitRequestId() != 0).findFirst();
        if (maybeNormalDataInOrder.isPresent()) {
            MercuryOrderDto clinicalOrderWithNormalData = maybeNormalDataInOrder.get();
            KitRequestShipping kitRequestShipping = KitRequestShipping.getKitRequest(
                    clinicalOrderWithNormalData.getDsmKitRequestId());
            phiManifest.setMfBarcode(kitRequestShipping.getKitLabel());
            phiManifest.setNormalCollaboratorSampleId(kitRequestShipping.getBspCollaboratorSampleId());
            phiManifest.setCollaboratorParticipantId(kitRequestShipping.getBspCollaboratorParticipantId());
            phiManifest.setCollectionDate(kitRequestShipping.getCollectionDate());
        }
        MercuryOrderDto mercuryOrderDto = orders.get(0);
        phiManifest.setClinicalOrderDate(DateTimeUtil.getDateFromEpoch(mercuryOrderDto.getOrderDate()));
        phiManifest.setClinicalOrderId(mercuryOrderDto.getOrderId());
        phiManifest.setClinicalPdoNumber(mercuryOrderDto.getMercuryPdoId());
        phiManifest.setOrderStatus(mercuryOrderDto.getOrderStatus());
        phiManifest.setOrderStatusDate(DateTimeUtil.getDateFromEpoch(mercuryOrderDto.getStatusDate()));

        String pediatricResponse = convertBooleanActivityAnswerToString(participant.getParticipantAnswerInSurvey(
                CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_CODE, SOMATIC_CONSENT_TUMOR_PEDIATRIC_QUESTION_STABLE_ID));
        phiManifest.setSomaticConsentTumorPediatricResponse(pediatricResponse);

        String assentAddendumResponse = convertBooleanActivityAnswerToString(participant.getParticipantAnswerInSurvey(
                CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_CODE, SOMATIC_ASSENT_ADDENDUM_QUESTION_STABLE_ID));
        phiManifest.setSomaticAssentAddendumResponse(assentAddendumResponse);
        Optional<Object> consentAnswer = getAdultParticipantConsentedToTumorAnswer(participant, ddpInstanceDto.getStudyGuid());

        if (consentAnswer.isPresent()) {
            phiManifest.setSomaticConsentTumorResponse(convertBooleanActivityAnswerToString(consentAnswer));
        } else {
            phiManifest.setSomaticConsentTumorResponse("");
        }
        return phiManifest;
    }

    private static String convertBooleanToYesNo(boolean b) {
        return b ? "Yes" : "No";
    }

    /**
     * Given an answer to a boolean question, returns Yes, No, or a blank string
     * to include in the PHI report.
     */
    public static String convertBooleanActivityAnswerToString(Optional<Object> answer) {
        if (answer.isPresent() && answer.get() instanceof Boolean) {
            return convertBooleanToYesNo((Boolean) answer.get());
        } else {
            if (answer.isPresent()) {
                return answer.get().toString();
            } else {
                return "";
            }
        }

    }

    public boolean isParticipantConsented(@NonNull ElasticSearchParticipantDto participant, @NonNull DDPInstanceDto ddpInstanceDto) {
        return hasParticipantConsentedToSharedLearning(participant, ddpInstanceDto) && hasParticipantConsentedToTumor(participant);
    }

    private boolean hasParticipantConsentedToTumor(ElasticSearchParticipantDto participant) {
        return participant.getDsm().isPresent() && participant.getDsm().get().isHasConsentedToTissueSample();
    }

    /**
     * If the participant has answered the question, it will be returned
     * as a boolean.  If they have not, an empty optional will be returned.
     */
    public static Optional<Object> getAdultParticipantConsentedToTumorAnswer(ElasticSearchParticipantDto participant, String studyGuid) {
        boolean hasCompletedConsentAddendum = participant.hasCompletedActivity(CONSENT_ADDENDUM_ACTIVITY_ACTIVITY_CODE);

        if (hasCompletedConsentAddendum) {
            if (DBConstants.LMS_STUDY_GUID.equals(studyGuid)) {
                return participant.getParticipantAnswerInSurvey(CONSENT_ADDENDUM_ACTIVITY_ACTIVITY_CODE, LMS_QUESTION_SOMATIC_CONSENT_ADDENDUM_TUMOR_STABLE_ID);
            } else if (DBConstants.OSTEO_STUDY_GUID.equals(studyGuid)) {
                return participant.getParticipantAnswerInSurvey(CONSENT_ADDENDUM_ACTIVITY_ACTIVITY_CODE, OS2_QUESTION_SOMATIC_CONSENT_ADDENDUM_TUMOR_STABLE_ID);
            }
        }
        return Optional.empty();
    }

    private boolean hasParticipantConsentedToSharedLearning(ElasticSearchParticipantDto participant, DDPInstanceDto ddpInstanceDto) {
        if (participant.getDsm().isEmpty()) {
            log.error("Participant {} does not have a DoB, which means we can't assess their consent", participant.getParticipantId());
            return false;
        }
        String dateOfBirth = participant.getDsm().get().getDateOfBirth();
        String dateOfMajority = (String) participant.getDsm().get().getDateOfMajority();
        String participantStatus = participant.getStatus().orElse("");
        if (StringUtils.isBlank(dateOfMajority) || participant.hasCompletedActivity(CONSENT_ACTIVITY_CODE)) {
            //self adult enrollment
            return (Boolean) getAdultParticipantConsentedToTumorAnswer(participant, ddpInstanceDto.getStudyGuid())
                    .orElse(false);
        } else {
            //pediatric or aged-up in lost to followup
            return isPediatricValidForPHI(participant, dateOfBirth, participantStatus);
        }
    }

    private static boolean isPediatricValidForPHI(ElasticSearchParticipantDto participant, String dateOfBirth, String participantStatus) {
        int age = DateTimeUtil.calculateAgeInYears(dateOfBirth);
        boolean hasCompletedPediatricConsentAddendum = participant.hasCompletedActivity(CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_CODE);

        if (hasCompletedPediatricConsentAddendum) {
            if (age >= 7 || participantStatus.equalsIgnoreCase(CONSENT_SUSPENDED)) {
                return participant.checkAnswerToActivity(CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_CODE,
                        SOMATIC_CONSENT_TUMOR_PEDIATRIC_QUESTION_STABLE_ID, true) && participant.checkAnswerToActivity(
                        CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_CODE, SOMATIC_ASSENT_ADDENDUM_QUESTION_STABLE_ID, true);
            }
            // else if age < 7
            return participant.checkAnswerToActivity(CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_CODE,
                    SOMATIC_CONSENT_TUMOR_PEDIATRIC_QUESTION_STABLE_ID, true);
        } else {
            return false;
        }
    }


}
