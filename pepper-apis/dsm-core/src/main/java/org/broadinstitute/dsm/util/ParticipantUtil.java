package org.broadinstitute.dsm.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.ddp.DDPActivityConstants;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants;

@Slf4j
public class ParticipantUtil {

    public static final String DDP_PARTICIPANT_ID = "ddpParticipantId";
    public static final String TRUE = "true";
    private static final Gson gson = new Gson();

    public static boolean isHruid(@NonNull String participantId) {
        final String hruidCheck = "^P\\w{5}$";
        return participantId.matches(hruidCheck);
    }

    public static boolean isGuid(@NonNull String participantId) {
        return participantId.length() == 20;
    }

    public static boolean matchesApplicantEmail(Profile applicantProfile,
                                                Map<String, String> applicantDataMap,
                                                Map<String, String> participantDataMap) {
        String currentParticipantEmail = participantDataMap.get(FamilyMemberConstants.EMAIL);
        if (StringUtils.isBlank(currentParticipantEmail)) {
            return false;
        }
        // There might be a case where profile doesn't have email, so fallback to data map.
        String applicantEmail = StringUtils.isNotBlank(applicantProfile.getEmail())
                ? applicantProfile.getEmail() : applicantDataMap.get(FamilyMemberConstants.EMAIL);
        if (StringUtils.isBlank(applicantEmail)) {
            return false;
        }
        // Email addresses are technically not case-sensitive, so we ignore case when comparing.
        return applicantEmail.equalsIgnoreCase(currentParticipantEmail);
    }

    public static boolean matchesApplicantEmail(String collaboratorParticipantId, List<ParticipantData> participantDatas) {
        String applicantEmail = null;
        String currentParticipantEmail = null;
        for (ParticipantData participantData : participantDatas) {
            Map<String, String> dataMap = participantData.getDataMap();
            if (dataMap == null) {
                continue;
            }
            if (!dataMap.containsKey(FamilyMemberConstants.COLLABORATOR_PARTICIPANT_ID)) {
                return false;
            }

            boolean isOldApplicant = dataMap.containsKey(FamilyMemberConstants.DATSTAT_ALTPID)
                    && dataMap.get(FamilyMemberConstants.DATSTAT_ALTPID).equals(participantData.getDdpParticipantId().orElse(""));
            boolean isNewApplicant = dataMap.containsKey(FamilyMemberConstants.IS_APPLICANT)
                    && TRUE.equals(dataMap.get(FamilyMemberConstants.IS_APPLICANT));
            boolean isCurrentParticipant = collaboratorParticipantId.equals(dataMap.get(FamilyMemberConstants.COLLABORATOR_PARTICIPANT_ID));

            if ((isNewApplicant || isOldApplicant) && isCurrentParticipant) {
                return true;
            } else {
                if (isNewApplicant || isOldApplicant) {
                    applicantEmail = dataMap.get(FamilyMemberConstants.EMAIL);
                }
                if (isCurrentParticipant) {
                    currentParticipantEmail = dataMap.get(FamilyMemberConstants.EMAIL);
                }
            }
        }
        return applicantEmail != null && applicantEmail.equalsIgnoreCase(currentParticipantEmail);
    }

    public static String getParticipantEmailById(String esParticipantIndex, String participantId) {
        if (StringUtils.isBlank(esParticipantIndex) || StringUtils.isBlank(participantId)) {
            throw new IllegalArgumentException();
        }
        StringBuilder email = new StringBuilder();
        ElasticSearchParticipantDto elasticSearchParticipantDto =
                ElasticSearchUtil.getParticipantESDataByParticipantId(esParticipantIndex, participantId);
        email.append(elasticSearchParticipantDto.getProfile()
                .map(Profile::getEmail)
                .orElse(""));
        return email.toString();
    }

    public static ParticipantData findApplicantData(String ddpParticipantId, List<ParticipantData> participantsDatas) {
        ParticipantData applicantData = null;
        for (ParticipantData participantData : participantsDatas) {
            Map<String, String> dataMap = participantData.getDataMap();
            if (dataMap == null) {
                continue;
            }
            boolean isOldApplicant = dataMap.containsKey(FamilyMemberConstants.DATSTAT_ALTPID)
                    && dataMap.get(FamilyMemberConstants.DATSTAT_ALTPID).equals(ddpParticipantId);
            boolean isNewApplicant = dataMap.containsKey(FamilyMemberConstants.IS_APPLICANT)
                    && TRUE.equals(dataMap.get(FamilyMemberConstants.IS_APPLICANT));
            if (isOldApplicant || isNewApplicant) {
                applicantData = participantData;
                break;
            }
        }
        return applicantData;
    }

    public static boolean isLegacyAltPid(String participantId) {
        return !isGuid(participantId);
    }

    public static String getParticipantGender(ElasticSearchParticipantDto participantByShortId, String realm, String ddpParticipantId) {
        String participantId = participantByShortId.getParticipantId();
        if (StringUtils.isBlank(participantId)) {
            throw new DsmInternalError(String.format("The participant %s is missing participant id", ddpParticipantId));
        }
        // if gender is set on tissue page use that
        List<String> list = new ArrayList();
        list.add(participantId);
        Map<String, List<OncHistoryDetail>> oncHistoryDetails = OncHistoryDetail.getOncHistoryDetailsByParticipantIds(realm, list);
        if (!oncHistoryDetails.isEmpty()) {
            Optional<OncHistoryDetail> oncHistoryWithGender = oncHistoryDetails.get(participantId).stream()
                    .filter(o -> StringUtils.isNotBlank(o.getGender())).findFirst();
            if (oncHistoryWithGender.isPresent()) {
                return oncHistoryWithGender.get().getGender();
            }
        }
        log.info("Participant {} did not have gender on tissue pages, will look into activities", participantByShortId.getParticipantId());
        //if gender is not set on tissue page get answer from "ABOUT_YOU.ASSIGNED_SEX"
        return getGenderFromActivities(participantByShortId.getActivities());
    }

    private static String getGenderFromActivities(List<Activities> activities) {
        Optional<Activities> maybeAboutYouActivity = activities.stream()
                .filter(activity -> DDPActivityConstants.ACTIVITY_ABOUT_YOU.equals(activity.getActivityCode()))
                .findFirst();
        return (String) maybeAboutYouActivity.map(aboutYou -> {
            List<Map<String, Object>> questionsAnswers = aboutYou.getQuestionsAnswers();
            Optional<Map<String, Object>> maybeGenderQuestionAnswer = questionsAnswers.stream()
                    .filter(q -> DDPActivityConstants.ABOUT_YOU_ACTIVITY_GENDER.equals(q.get(DDPActivityConstants.DDP_ACTIVITY_STABLE_ID)))
                    .findFirst();
            return maybeGenderQuestionAnswer
                    .map(answer -> answer.get(DDPActivityConstants.ACTIVITY_QUESTION_ANSWER))
                    .orElse("U");
        }).orElse("U");
    }
}
