package org.broadinstitute.dsm.service.phimanifest;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.mercury.MercuryOrderDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.mercury.MercuryOrderDto;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import spark.QueryParamsMap;

@Slf4j
public class PhiManifestService {

    public Object generateReport(QueryParamsMap queryParams, String realm) {
        String sequencingOrderNumber = queryParams.get(RoutePath.SEQ_ORDER_NUMBER).value();
        String ddpParticipantId = queryParams.get(RoutePath.DDP_PARTICIPANT_ID).value();
        DDPInstanceDto ddpInstanceDto = DDPInstanceDao.of().getDDPInstanceByInstanceName(realm).orElseThrow();
        String maybeErrorMessage = null;
        if (StringUtils.isBlank(ddpParticipantId)) {
            maybeErrorMessage = "No DdpParticipantId was provided";
        }
        if (StringUtils.isBlank(sequencingOrderNumber)) {
            maybeErrorMessage = "No sequencingOrderNumber was provided";
        }
        if (!isSequencingOrderValid(sequencingOrderNumber, ddpParticipantId, ddpInstanceDto)) {
            maybeErrorMessage = String.format("Sequencing order number %s is not a valid order for participant %s ", sequencingOrderNumber,
                    ddpParticipantId);
        }
        if (!isParticipantConsented(ddpParticipantId, ddpInstanceDto)) {
            maybeErrorMessage = String.format("Participant %s has not consented to receive shared learning ", ddpParticipantId);
        }
        if (StringUtils.isNotBlank(maybeErrorMessage)) {
            log.warn(maybeErrorMessage);
            throw new DSMBadRequestException(maybeErrorMessage);
        }
        return generateReport(ddpParticipantId, sequencingOrderNumber, ddpInstanceDto);
    }

    private Object generateReport(String ddpParticipantId, String sequencingOrderNumber, DDPInstanceDto ddpInstanceDto) {
        return null;
    }

    public boolean isParticipantConsented(@NonNull String ddpParticipantId, @NonNull DDPInstanceDto ddpInstanceDto) {
        ElasticSearchParticipantDto participant = ElasticSearchUtil.getParticipantESDataByParticipantId(
                ddpInstanceDto.getEsParticipantIndex(), ddpParticipantId);
        return hasParticipantConsentedToSharedLearning(participant) && hasParticipantConsentedToTumor(participant,
                ddpInstanceDto.getStudyGuid());
    }

    private boolean hasParticipantConsentedToTumor(ElasticSearchParticipantDto participant, String studyGuid) {
        if (DBConstants.LMS_STUDY_GUID.equals(studyGuid)) {
            return checkAnswerToActivity(participant, "CONSENT_ADDENDUM", "SOMATIC_CONSENT_ADDENDUM_TUMOR", true);
        } else if (DBConstants.OSTEO_STUDY_GUID.equals(studyGuid)) {
            return checkAnswerToActivity(participant, "CONSENT_ADDENDUM", "SOMATIC_CONSENT_TUMOR", true);
        }
        return false;
    }

    private boolean hasParticipantConsentedToSharedLearning(ElasticSearchParticipantDto participant) {
        String dateOfBirth = participant.getDsm().get().getDateOfBirth();
        int age = calculateAge(dateOfBirth);
        if (age >= 7) {
            return checkAnswerToActivity(participant, "CONSENT_ADDENDUM_PEDIATRIC", "SOMATIC_CONSENT_TUMOR_PEDIATRIC",
                    true) && checkAnswerToActivity(participant, "CONSENT_ADDENDUM_PEDIATRIC",
                    "SOMATIC_ASSENT_ADDENDUM", true);

        } else if (age < 7) {
            return checkAnswerToActivity(participant, "CONSENT_ADDENDUM_PEDIATRIC", "SOMATIC_CONSENT_TUMOR_PEDIATRIC",
                    true);
        }
        return false;
    }

    /** checks if the answer provided by a participant to a survey question matches the criteria
     * @param participant participant data from ES.
     * */
    private boolean checkAnswerToActivity(ElasticSearchParticipantDto participant, String activityName, String question, Object answer) {
        Optional<Activities> maybeActivity = participant.getActivities().stream().filter(activities -> activities.getActivityCode()
                .equals(activityName)).findAny();
        if (maybeActivity.isPresent()) {
            Activities activities = maybeActivity.get();
            Optional<Map<String, Object>> maybeQuestionAnswers = activities.getQuestionsAnswers()
                    .stream().filter(questionAnswer -> questionAnswer.get("stableId").equals(question)).findAny();
            if (maybeQuestionAnswers.isPresent()) {
                Map<String, Object> questionAnswer = maybeQuestionAnswers.get();
                if (questionAnswer.containsKey("answer") && questionAnswer.get("answer").equals(answer)) {
                    return true;
                }
            }
        }
        return false;
    }


    @VisibleForTesting
    public boolean isSequencingOrderValid(@NonNull String sequencingOrderId, @NonNull String ddpParticipantId,
                                          @NonNull DDPInstanceDto ddpInstanceDto) {
        List<MercuryOrderDto> orders = new MercuryOrderDao().getByOrderId(sequencingOrderId);
        if (orders.isEmpty() || orders.stream().anyMatch(order -> StringUtils.isBlank(order.getMercuryPdoId())
                || ddpInstanceDto.getDdpInstanceId() != order.getDdpInstanceId()
                || !ddpParticipantId.equals(order.getDdpParticipantId()))) {
            return false;
        }
        return true;
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
}
