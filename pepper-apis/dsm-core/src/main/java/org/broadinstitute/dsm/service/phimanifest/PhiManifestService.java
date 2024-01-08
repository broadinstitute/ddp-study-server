package org.broadinstitute.dsm.service.phimanifest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Slf4j
public class PhiManifestService {
    private static final String CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_STABLE_ID = "CONSENT_ADDENDUM_PEDIATRIC";
    private static final String SOMATIC_CONSENT_TUMOR_PEDIATRIC_QUESTION = "SOMATIC_CONSENT_TUMOR_PEDIATRIC";
    private static final String SOMATIC_ASSENT_ADDENDUM_QUESTION = "SOMATIC_ASSENT_ADDENDUM";
    private static final String CONSENT_ADDENDUM_ACTIVITY_STABLE_ID = "CONSENT_ADDENDUM";
    private static final String LMS_QUESTION_SOMATIC_CONSENT_ADDENDUM_TUMOR = "SOMATIC_CONSENT_ADDENDUM_TUMOR";
    private static final String OS2_QUESTION_SOMATIC_CONSENT_ADDENDUM_TUMOR = "SOMATIC_CONSENT_TUMOR";

    private static final String NOT_VALID_ORDER_ERROR =
            "Sequencing order number %s does not exist or is not a valid clinical order for this participant";

    private static final String NOT_CONSENTED_ERROR = "Participant %s has not consented to receive shared learning";


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
        if (!isParticipantConsented(ddpParticipantId, ddpInstanceDto)) {
            String errorMessage = String.format(NOT_CONSENTED_ERROR, ddpParticipantId);
            log.warn(errorMessage);
            return new PhiManifestResponse(errorMessage);
        }
        ElasticSearchParticipantDto participant = ElasticSearchUtil.getParticipantESDataByParticipantId(
                ddpInstanceDto.getEsParticipantIndex(), ddpParticipantId);
        if (participant.getProfile().isEmpty()) {
            throw new DsmInternalError("There is no profile in ES for participant " + ddpParticipantId);
        }
        PhiManifest finalPhiManifest = generateDataForReport(participant, orders, ddpInstanceDto);
        return finalPhiManifest.toResponseArray(ddpParticipantId, sequencingOrderId);
    }

    /**
     * Creates a PhiManifest from the information in participant and in a clinical order
     */
    public PhiManifest generateDataForReport(@NonNull ElasticSearchParticipantDto participant, @NonNull List<MercuryOrderDto> orders,
                                             @NonNull DDPInstanceDto ddpInstanceDto) {
        //This method assumes that each order has at most 1 Tumor and at most 1 Normal sample, which is a correct assumption based on
        // clinical ordering criteria currently in place
        PhiManifest phiManifest = new PhiManifest();
        Profile participantProfile = participant.getProfile().orElseThrow();
        phiManifest.setShortId(participantProfile.getHruid());
        phiManifest.setFirstName(participantProfile.getFirstName());
        phiManifest.setLastName(participantProfile.getLastName());
        List<String> proxies = participant.getProxies();
        if (proxies != null && !proxies.isEmpty()) {
            String proxyParticipantId = proxies.get(0);
            ElasticSearchParticipantDto proxyParticipant = ElasticSearchUtil.getParticipantESDataByParticipantId(
                    ddpInstanceDto.getEsParticipantIndex(), proxyParticipantId);
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
            if (StringUtils.isNotBlank(oncHistoryDetail.getAdditionalValuesJson())) {
                try {
                    Map<String, String> oncHistoryAdditionalValues =
                            new ObjectMapper().readValue(oncHistoryDetail.getAdditionalValuesJson(), HashMap.class);
                    phiManifest.setSampleType(oncHistoryAdditionalValues.getOrDefault("FFPE", null));
                } catch (JsonProcessingException e) {
                    throw new DsmInternalError(
                            String.format("Unable to read additional values from oncHistory with id %d for participant %s",
                                    oncHistoryDetail.getOncHistoryDetailId(), participantProfile.getGuid()), e);
                }
            }
            phiManifest.setBlockId(tissue.getBlockIdShl());
            phiManifest.setTumorCollaboratorSampleId(tissue.getCollaboratorSampleId());
            phiManifest.setTissueSite(tissue.getTissueSite());
            phiManifest.setSequencingResults(tissue.getTissueSequence());
        }
        Optional<MercuryOrderDto> maybeNormalDataInOrder = orders.stream().filter(mercuryOrderDto -> mercuryOrderDto.getDsmKitRequestId()
                != null && mercuryOrderDto.getDsmKitRequestId() != 0).findFirst();
        if (maybeNormalDataInOrder.isPresent()) {
            MercuryOrderDto clinicalOrderWithNormalData = maybeNormalDataInOrder.get();
            KitRequestShipping kitRequestShipping = KitRequestShipping.getKitRequest(
                    String.valueOf(clinicalOrderWithNormalData.getDsmKitRequestId()));
            phiManifest.setMfBarcode(kitRequestShipping.getKitLabel());
            phiManifest.setNormalCollaboratorSampleId(kitRequestShipping.getBspCollaboratorSampleId());
        }
        MercuryOrderDto mercuryOrderDto = orders.get(0);
        phiManifest.setClinicalOrderDate(DateTimeUtil.getDateFromEpoch(mercuryOrderDto.getOrderDate()));
        phiManifest.setClinicalOrderId(mercuryOrderDto.getOrderId());
        phiManifest.setClinicalPdoNumber(mercuryOrderDto.getMercuryPdoId());
        phiManifest.setOrderStatus(mercuryOrderDto.getOrderStatus());
        phiManifest.setOrderStatusDate(DateTimeUtil.getDateFromEpoch(mercuryOrderDto.getStatusDate()));

        String pediatricResponse = convertActivityAnswerValue((Boolean) participant.getParticipantAnswerInSurvey(
                CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_STABLE_ID, SOMATIC_CONSENT_TUMOR_PEDIATRIC_QUESTION).orElse(false));
        phiManifest.setSomaticConsentTumorPediatricResponse(pediatricResponse);
        String assentAddendumResponse = convertActivityAnswerValue((Boolean) participant.getParticipantAnswerInSurvey(
                CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_STABLE_ID, SOMATIC_ASSENT_ADDENDUM_QUESTION).orElse(false));
        phiManifest.setSomaticAssentAddendumResponse(assentAddendumResponse);
        String consentAnswer = convertActivityAnswerValue(hasAdultParticipantConsentedToTumor(participant, ddpInstanceDto.getStudyGuid()));
        phiManifest.setSomaticConsentTumorResponse(consentAnswer);
        return phiManifest;
    }

    private String convertActivityAnswerValue(boolean b) {
        return b ? "Yes" : "No";
    }

    public boolean isParticipantConsented(@NonNull String ddpParticipantId, @NonNull DDPInstanceDto ddpInstanceDto) {
        ElasticSearchParticipantDto participant = ElasticSearchUtil.getParticipantESDataByParticipantId(
                ddpInstanceDto.getEsParticipantIndex(), ddpParticipantId);
        return participant != null && hasParticipantConsentedToSharedLearning(participant, ddpInstanceDto)
                && hasParticipantConsentedToTumor(participant);
    }

    private boolean hasParticipantConsentedToTumor(ElasticSearchParticipantDto participant) {
        return participant.getDsm().isPresent() && participant.getDsm().get().isHasConsentedToTissueSample();
    }

    private boolean hasAdultParticipantConsentedToTumor(ElasticSearchParticipantDto participant, String studyGuid) {
        boolean answer = false;
        if (DBConstants.LMS_STUDY_GUID.equals(studyGuid)) {
            answer = participant.checkAnswerToActivity(CONSENT_ADDENDUM_ACTIVITY_STABLE_ID, LMS_QUESTION_SOMATIC_CONSENT_ADDENDUM_TUMOR,
                    true);
        } else if (DBConstants.OSTEO_STUDY_GUID.equals(studyGuid)) {
            answer = participant.checkAnswerToActivity(CONSENT_ADDENDUM_ACTIVITY_STABLE_ID, OS2_QUESTION_SOMATIC_CONSENT_ADDENDUM_TUMOR,
                    true);
        }
        return answer;
    }

    private boolean hasParticipantConsentedToSharedLearning(ElasticSearchParticipantDto participant, DDPInstanceDto ddpInstanceDto) {
        if (participant.getDsm().isEmpty()) {
            log.error("Participant {} does not have a DoB, which means we can't assess their consent", participant.getParticipantId());
            return false;
        }
        String dateOfBirth = participant.getDsm().get().getDateOfBirth();
        String dateOfMajority = (String) participant.getDsm().get().getDateOfMajority();
        if (StringUtils.isBlank(dateOfMajority) || DateTimeUtil.isAdult(dateOfMajority)) {
            return hasAdultParticipantConsentedToTumor(participant, ddpInstanceDto.getStudyGuid());
        }
        int age = DateTimeUtil.calculateAgeInYears(dateOfBirth);
        if (age >= 7) {
            return participant.checkAnswerToActivity(CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_STABLE_ID,
                    SOMATIC_CONSENT_TUMOR_PEDIATRIC_QUESTION, true) && participant.checkAnswerToActivity(
                    CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_STABLE_ID, SOMATIC_ASSENT_ADDENDUM_QUESTION, true);

        } else if (age < 7) {
            return participant.checkAnswerToActivity(CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_STABLE_ID,
                    SOMATIC_CONSENT_TUMOR_PEDIATRIC_QUESTION, true);
        }
        return false;
    }


}
