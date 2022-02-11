package org.broadinstitute.dsm.util;

import com.google.gson.Gson;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.model.elastic.ESProfile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants;

import java.util.List;
import java.util.Map;

public class ParticipantUtil {

    private static final Gson gson = new Gson();

    public static final String DDP_PARTICIPANT_ID = "ddpParticipantId";
    public static final String TRUE = "true";

    public static boolean isHruid(@NonNull String participantId) {
        final String hruidCheck = "^P\\w{5}$";
        return participantId.matches(hruidCheck);
    }

    public static boolean isGuid(@NonNull String participantId) {
        return participantId.length() == 20;
    }

    public static boolean isLegacyAltPid(@NonNull String participantId) {
        return participantId.length() > 20;
    }

    public static boolean matchesApplicantEmail(ESProfile applicantProfile,
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
        String applicantEmail = null, currentParticipantEmail = null;
        for (ParticipantData participantData: participantDatas) {
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

    public static String getParticipantEmailById(String esParticipantIndex, String pId) {
        if (StringUtils.isBlank(esParticipantIndex) || StringUtils.isBlank(pId)) throw new IllegalArgumentException();
        StringBuilder email = new StringBuilder();
        ElasticSearchParticipantDto elasticSearchParticipantDto =
                ElasticSearchUtil.getParticipantESDataByParticipantId(esParticipantIndex, pId)
                        .orElse(new ElasticSearchParticipantDto.Builder().build());
        email.append(elasticSearchParticipantDto.getProfile()
                .map(ESProfile::getEmail)
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
}
