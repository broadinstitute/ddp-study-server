package org.broadinstitute.dsm.db.dto.kit;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.model.ddp.DDPActivityConstants;
import org.broadinstitute.dsm.model.elastic.ESActivities;
import org.broadinstitute.dsm.model.elastic.ESDsm;
import org.broadinstitute.dsm.model.elastic.ESProfile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Data
public class ClinicalKitDto {

    @SerializedName("participant_id")
    String collaboratorParticipantId;

    @SerializedName("sample_id")
    String sampleId;

    @SerializedName("sample_collection")
    String sampleCollection;

    @SerializedName("material_type")
    String materialType;

    @SerializedName("vessel_type")
    String vesselType;

    @SerializedName("first_name")
    String firstName;

    @SerializedName("last_name")
    String lastName;

    @SerializedName("date_of_birth")
    String dateOfBirth;

    @SerializedName("sample_type")
    String sampleType;

    @SerializedName("gender")
    String gender;

    @SerializedName("accession_number")
    String accessionNumber;

    @SerializedName("collection_date")
    String collectionDate;

    @SerializedName("kit_label")
    String mfBarcode;

    private static final Logger logger = LoggerFactory.getLogger(ClinicalKitDto.class);

    public ClinicalKitDto(String collaboratorParticipantId, String sampleId, String sampleCollection, String materialType, String vesselType,
                          String firstName, String lastName, String dateOfBirth, String sampleType, String gender, String accessionNumber, String mfBarcode) {
        this.collaboratorParticipantId = collaboratorParticipantId;
        this.sampleId = sampleId;
        this.sampleCollection = sampleCollection;
        this.materialType = materialType;
        this.vesselType = vesselType;
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.sampleType = sampleType;
        this.gender = gender;
        this.accessionNumber = accessionNumber;
        this.mfBarcode = mfBarcode;
    }

    public ClinicalKitDto() {
    }

    public void setSampleType(String kitType) {
        switch (kitType.toLowerCase()) {
            case "saliva":
                this.sampleType = "Normal";
                break;
            case "blood":
                this.sampleType = "N/A";
                break;
            default: //tissue
                this.sampleType = "Tumor";
                break;
        }
    }

    public void setGender(String genderString) {
        switch (genderString.toLowerCase()) {
            case "male":
                this.gender = "M";
                break;
            case "female":
                this.gender = "F";
                break;
            default: //intersex or prefer_not_answer
                this.gender = "U";
                break;
        }
    }

    public void setNecessaryParticipantDataToClinicalKit(String ddpParticipantId, DDPInstance ddpInstance) {
        Optional<ElasticSearchParticipantDto> maybeParticipantESDataByParticipantId =
                ElasticSearchUtil.getParticipantESDataByParticipantId(ddpInstance.getParticipantIndexES(), ddpParticipantId);
        if (maybeParticipantESDataByParticipantId.isEmpty()) {
            throw new RuntimeException("Participant ES Data is not found for " + ddpParticipantId);
        }

        try {
            this.setDateOfBirth(maybeParticipantESDataByParticipantId.get().getDsm().map(ESDsm::getDateOfBirth).orElse(""));
            this.setFirstName(maybeParticipantESDataByParticipantId.get().getProfile().map(ESProfile::getFirstName).orElse(""));
            this.setLastName(maybeParticipantESDataByParticipantId.get().getProfile().map(ESProfile::getLastName).orElse(""));
            this.setGender(getParticipantGender(maybeParticipantESDataByParticipantId.get(), ddpInstance.getName()));
            String shortId = maybeParticipantESDataByParticipantId.get().getProfile().map(ESProfile::getHruid).orElse("");
            String collaboratorParticipantId = KitRequestShipping.getCollaboratorParticipantId(ddpInstance.getBaseUrl(), ddpInstance.getDdpInstanceId(), ddpInstance.isMigratedDDP(),
                    ddpInstance.getCollaboratorIdPrefix(), ddpParticipantId, shortId, null);
            this.setCollaboratorParticipantId(collaboratorParticipantId);
        } catch (Exception e) {
            throw new RuntimeException("Participant doesn't exist / is not valid for kit " + e.getMessage());
        }
    }

    private String getParticipantGender(ElasticSearchParticipantDto participantByShortId, String realm) {
        // if gender is set on tissue page use that
        List<String> list = new ArrayList();
        list.add(participantByShortId.getParticipantId());
        Map<String, List<OncHistoryDetail>> oncHistoryDetails = OncHistoryDetail.getOncHistoryDetailsByParticipantIds(realm, list);
        if (!oncHistoryDetails.isEmpty()) {
            Optional<OncHistoryDetail> oncHistoryWithGender = oncHistoryDetails.get(participantByShortId.getParticipantId()).stream().filter(o -> StringUtils.isNotBlank(o.getGender())).findFirst();
            if (oncHistoryWithGender.isPresent()) {
                return oncHistoryWithGender.get().getGender();
            }
        }
        //if gender is not set on tissue page get answer from "ABOUT_YOU.ASSIGNED_SEX"
        return getGenderFromActivities(participantByShortId.getActivities());
    }

    private String getGenderFromActivities(List<ESActivities> activities) {
        Optional<ESActivities> maybeAboutYouActivity = activities.stream()
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
