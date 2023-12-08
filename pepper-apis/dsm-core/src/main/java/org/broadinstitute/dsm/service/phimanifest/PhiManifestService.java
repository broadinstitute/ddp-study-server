package org.broadinstitute.dsm.service.phimanifest;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Period;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
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
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.phimanifest.PhiManifest;
import org.broadinstitute.dsm.model.phimanifest.PhiManifestResponse;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ParticipantUtil;

@Slf4j
public class PhiManifestService {
    private static final String CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_STABLE_ID = "CONSENT_ADDENDUM_PEDIATRIC";
    private static final String SOMATIC_CONSENT_TUMOR_PEDIATRIC_QUESTION = "SOMATIC_CONSENT_TUMOR_PEDIATRIC";
    private static final String SOMATIC_ASSENT_ADDENDUM_QUESTION = "SOMATIC_ASSENT_ADDENDUM";
    private static final String CONSENT_ADDENDUM_ACTIVITY_STABLE_ID = "CONSENT_ADDENDUM";
    private static final String LMS_QUESTION_SOMATIC_CONSENT_ADDENDUM_TUMOR = "SOMATIC_CONSENT_ADDENDUM_TUMOR";
    private static final String OS2_QUESTION_SOMATIC_CONSENT_ADDENDUM_TUMOR = "SOMATIC_CONSENT_TUMOR";

    private final String[] headers = new String[]{"Short Id", "First Name", "Last Name", "Proxy First Name", "Proxy Last Name",
            "Date of Birth", "Gender", "Somatic Assent Addendum Response", "Somatic Consent Tumor Pediatric Response",
            "somatic Consent Tumor Response", "Date of PX", "Facility Name", "Sample Type", "Accession Number", "Histology",
            "Block Id", "Tumor Collaborator Sample Id", "Tissue Site", "Sequencing Results", "Normal Manufacturer Barcode",
            "Normal Collaborator Sample Id", "Clinical Order Date", "Clinical Order Id", "Clinical PDO Number", "Order Status",
            "Order Status Date"};


    public PhiManifestResponse generateReport(String ddpParticipantId, String sequencingOrderId, DDPInstanceDto ddpInstanceDto) {
        if (!isSequencingOrderValid(sequencingOrderId, ddpParticipantId, ddpInstanceDto)) {
            String errorMessage = String.format("Sequencing order number %s is not a valid order for participant", sequencingOrderId);
            log.warn(errorMessage);
            return new PhiManifestResponse(errorMessage);
        }
        if (!isParticipantConsented(ddpParticipantId, ddpInstanceDto)) {
            String errorMessage = String.format("Participant %s has not consented to receive shared learning ", ddpParticipantId);
            log.warn(errorMessage);
            return new PhiManifestResponse(errorMessage);
        }
        ElasticSearchParticipantDto participant = ElasticSearchUtil.getParticipantESDataByParticipantId(
                ddpInstanceDto.getEsParticipantIndex(), ddpParticipantId);
        if (participant.getProfile().isEmpty()) {
            throw new DsmInternalError("There is no profile in ES for participant " + ddpParticipantId);
        }
        List<MercuryOrderDto> orders = new MercuryOrderDao().getByOrderId(sequencingOrderId);
        PhiManifest finalPhiManifest = generateDataForReport(participant, orders, ddpInstanceDto);
        return createResponse(finalPhiManifest, ddpParticipantId, sequencingOrderId);
    }

    private PhiManifestResponse createResponse(PhiManifest finalPhiManifest, String ddpParticipantId, String sequencingOrderId) {
        String[][] report = new String[2][headers.length];

        String[] data = new String[headers.length];
        data[0] = finalPhiManifest.getShortId();
        data[1] = finalPhiManifest.getFirstName();
        data[2] = finalPhiManifest.getLastName();
        data[3] = finalPhiManifest.getProxyFirstName();
        data[4] = finalPhiManifest.getProxyLastName();
        data[5] = finalPhiManifest.getDateOfBirth();
        data[6] = finalPhiManifest.getGender();
        data[7] = finalPhiManifest.getSomaticAssentAddendumResponse();
        data[8] = finalPhiManifest.getSomaticConsentTumorPediatricResponse();
        data[9] = finalPhiManifest.getSomaticConsentTumorResponse();
        data[10] = finalPhiManifest.getDateOfPx();
        data[11] = finalPhiManifest.getFacility();
        data[12] = finalPhiManifest.getSampleType();
        data[13] = finalPhiManifest.getAccessionNumber();
        data[14] = finalPhiManifest.getHistology();
        data[15] = finalPhiManifest.getBlockId();
        data[16] = finalPhiManifest.getTumorCollaboratorSampleId();
        data[17] = finalPhiManifest.getTissueSite();
        data[18] = finalPhiManifest.getSequencingResults();
        data[19] = finalPhiManifest.getMfBarcode();
        data[20] = finalPhiManifest.getNormalCollaboratorSampleId();
        data[21] = finalPhiManifest.getClinicalOrderDate();
        data[22] = finalPhiManifest.getClinicalOrderId();
        data[23] = finalPhiManifest.getClinicalPdoNumber();
        data[24] = finalPhiManifest.getOrderStatus();
        data[25] = finalPhiManifest.getOrderStatusDate();

        report[0] = headers;
        report[1] = data;
        PhiManifestResponse phiManifestResponse = new PhiManifestResponse();
        phiManifestResponse.setData(report);
        phiManifestResponse.setOrderId(sequencingOrderId);
        phiManifestResponse.setDdpParticipantId(ddpParticipantId);
        return phiManifestResponse;
    }

    /**Creates a PhiManifest from the information in participant and in a clinical order*/
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
        if (proxies != null && proxies.isEmpty()) {
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
        String participantGenderInDsm = ParticipantUtil.getParticipantGender(participant, ddpInstanceDto.getInstanceName(),
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
            try {
                Map<String, String> oncHistoryAdditionalValues =
                        new ObjectMapper().readValue(oncHistoryDetail.getAdditionalValuesJson(), HashMap.class);
                phiManifest.setSampleType(oncHistoryAdditionalValues.getOrDefault("FFPE", null));
            } catch (JsonProcessingException e) {
                throw new DsmInternalError(String.format("Unable to read additional values from oncHistory with id %d for participant %s",
                        oncHistoryDetail.getOncHistoryDetailId(), participantProfile.getGuid()), e);
            }
            phiManifest.setBlockId(tissue.getBlockIdShl());
            phiManifest.setTumorCollaboratorSampleId(tissue.getCollaboratorSampleId());
            phiManifest.setTissueSite(tissue.getTissueSite());
            phiManifest.setSequencingResults(tissue.getTissueSequence());
        }
        Optional<MercuryOrderDto> maybeNormalDataInOrder = orders.stream().filter(mercuryOrderDto -> mercuryOrderDto.getDsmKitRequestId()
                != null).findFirst();
        if (maybeNormalDataInOrder.isPresent()) {
            MercuryOrderDto clinicalOrderWithNormalData = maybeNormalDataInOrder.get();
            KitRequestShipping kitRequestShipping = KitRequestShipping.getKitRequest(
                    String.valueOf(clinicalOrderWithNormalData.getDsmKitRequestId()));
            phiManifest.setMfBarcode(kitRequestShipping.getKitLabel());
            phiManifest.setNormalCollaboratorSampleId(kitRequestShipping.getBspCollaboratorSampleId());
        }
        MercuryOrderDto mercuryOrderDto = orders.get(0);
        phiManifest.setClinicalOrderDate(getDateFromEpoch(mercuryOrderDto.getOrderDate()));
        phiManifest.setClinicalOrderId(mercuryOrderDto.getOrderId());
        phiManifest.setClinicalPdoNumber(mercuryOrderDto.getMercuryPdoId());
        phiManifest.setOrderStatus(mercuryOrderDto.getOrderStatus());
        phiManifest.setOrderStatusDate(getDateFromEpoch(mercuryOrderDto.getStatusDate()));
        
        phiManifest.setSomaticConsentTumorPediatricResponse(String.valueOf(getParticipantAnswerInSurvey(participant,
                CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_STABLE_ID, SOMATIC_CONSENT_TUMOR_PEDIATRIC_QUESTION).orElse(false)));
        phiManifest.setSomaticAssentAddendumResponse(String.valueOf(getParticipantAnswerInSurvey(participant,
                CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_STABLE_ID, SOMATIC_ASSENT_ADDENDUM_QUESTION).orElse(false)));
        phiManifest.setSomaticConsentTumorResponse(String.valueOf(hasParticipantConsentedToTumor(participant, 
                ddpInstanceDto.getStudyGuid())));
        return phiManifest;
    }

    public boolean isParticipantConsented(@NonNull String ddpParticipantId, @NonNull DDPInstanceDto ddpInstanceDto) {
        ElasticSearchParticipantDto participant = ElasticSearchUtil.getParticipantESDataByParticipantId(
                ddpInstanceDto.getEsParticipantIndex(), ddpParticipantId);
        return participant != null && hasParticipantConsentedToSharedLearning(participant) && hasParticipantConsentedToTumor(participant,
                ddpInstanceDto.getStudyGuid());
    }

    private boolean hasParticipantConsentedToTumor(ElasticSearchParticipantDto participant, String studyGuid) {
        if (DBConstants.LMS_STUDY_GUID.equals(studyGuid)) {
            return checkAnswerToActivity(participant, CONSENT_ADDENDUM_ACTIVITY_STABLE_ID, LMS_QUESTION_SOMATIC_CONSENT_ADDENDUM_TUMOR,
                    true);
        } else if (DBConstants.OSTEO_STUDY_GUID.equals(studyGuid)) {
            return checkAnswerToActivity(participant, CONSENT_ADDENDUM_ACTIVITY_STABLE_ID, OS2_QUESTION_SOMATIC_CONSENT_ADDENDUM_TUMOR,
                    true);
        }
        return false;
    }

    private boolean hasParticipantConsentedToSharedLearning(ElasticSearchParticipantDto participant) {
        if (participant.getDsm().isEmpty()) {
            log.error("Participant {} does not have a DoB, which means we can't assess their consent", participant.getParticipantId());
            return false;
        }
        String dateOfBirth = participant.getDsm().get().getDateOfBirth();
        int age = calculateAge(dateOfBirth);
        if (age >= 7) {
            return checkAnswerToActivity(participant, CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_STABLE_ID,
                    SOMATIC_CONSENT_TUMOR_PEDIATRIC_QUESTION, true) && checkAnswerToActivity(participant,
                    CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_STABLE_ID, SOMATIC_ASSENT_ADDENDUM_QUESTION, true);

        } else if (age < 7) {
            return checkAnswerToActivity(participant, CONSENT_ADDENDUM_PEDIATRICS_ACTIVITY_STABLE_ID,
                    SOMATIC_CONSENT_TUMOR_PEDIATRIC_QUESTION, true);
        }
        return false;
    }

    /**
     * checks if the answer provided by a participant to a survey question matches the criteria
     *
     * @param participant  participant data from ES.
     * @param activityName stable id of the activity
     * @param question     stable id of the question
     * @param answer       desired answer
     */
    private boolean checkAnswerToActivity(ElasticSearchParticipantDto participant, String activityName, String question, Object answer) {
        Optional<Object> possibleAnswer = getParticipantAnswerInSurvey(participant, activityName, question);
        return possibleAnswer.isPresent() && possibleAnswer.get().equals(answer);

    }

    private Optional<Object> getParticipantAnswerInSurvey(ElasticSearchParticipantDto participant, String activityName, String question) {
        Optional<Activities> maybeActivity = participant.getActivities().stream().filter(activities -> activities.getActivityCode()
                .equals(activityName)).findAny();
        if (maybeActivity.isPresent()) {
            Activities activities = maybeActivity.get();
            Optional<Map<String, Object>> maybeQuestionAnswers = activities.getQuestionsAnswers()
                    .stream().filter(questionAnswer -> questionAnswer.get("stableId").equals(question)).findAny();
            if (maybeQuestionAnswers.isPresent()) {
                Map<String, Object> questionAnswer = maybeQuestionAnswers.get();
                if (questionAnswer.containsKey("answer")) {
                    return Optional.ofNullable(questionAnswer.get("answer"));
                }
            }
        }
        return Optional.empty();
    }


    @VisibleForTesting
    public boolean isSequencingOrderValid(@NonNull String sequencingOrderId, @NonNull String ddpParticipantId,
                                          @NonNull DDPInstanceDto ddpInstanceDto) {
        List<MercuryOrderDto> orders = new MercuryOrderDao().getByOrderId(sequencingOrderId);
        return !(orders.isEmpty() || orders.stream().anyMatch(order -> StringUtils.isBlank(order.getMercuryPdoId())
                || ddpInstanceDto.getDdpInstanceId() != order.getDdpInstanceId()
                || !ddpParticipantId.equals(order.getDdpParticipantId())));
    }

    public int calculateAge(@NonNull String dateOfBirth) {
        LocalDate dob = LocalDate.parse(dateOfBirth);
        LocalDate curDate = LocalDate.now();
        if ((dob != null) && (curDate != null)) {
            return Period.between(dob, curDate).getYears();
        } else {
            throw new DsmInternalError("Could not calculate age for dateOfBirth " + dateOfBirth);
        }
    }

    public String getDateFromEpoch(long epoch) {
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
        return sdf.format(new Date(epoch));
    }
}
