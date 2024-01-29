package org.broadinstitute.dsm.model.elastic.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.annotations.VisibleForTesting;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.ddp.DDPActivityConstants;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.Address;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.ESComputed;
import org.broadinstitute.dsm.model.elastic.Files;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.statics.ESObjectConstants;

@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
public class ElasticSearchParticipantDto {

    private Address address;
    private List<Object> medicalProviders;
    private List<Object> invitations;
    private List<Activities> activities;
    private List<String> governedUsers;
    private ESComputed computed;
    private Long statusTimestamp;
    private Profile profile;
    private List<Files> files;
    private List<String> proxies;
    private List<Map<String, Object>> workflows;
    private String status;
    private Dsm dsm;
    private String queriedParticipantId;
    @Getter
    private String ddp;

    private ElasticSearchParticipantDto(ElasticSearchParticipantDto.Builder builder) {
        this.address = builder.address;
        this.medicalProviders = builder.medicalProviders;
        this.invitations = builder.invitations;
        this.activities = builder.activities;
        this.statusTimestamp = builder.statusTimeStamp;
        this.profile = builder.profile;
        this.files = builder.files;
        this.proxies = builder.proxies;
        this.workflows = builder.workflows;
        this.status = builder.status;
        this.dsm = builder.dsm;
        this.computed = builder.computed;
        this.governedUsers = builder.governedUsers;
        this.queriedParticipantId = builder.queriedParticipantId;
    }

    protected ElasticSearchParticipantDto() {  }

    /**
     * Changes the value for the given question's answer.
     * Does not make any modification to underlying elastic data.
     * @return true if the value was changed, false if the activity
     * and question do not exist.
     */
    @VisibleForTesting
    public boolean changeQuestionAnswer(String activityCode, String questionStableId, Object value) {
        for (Activities activity : getActivities()) {
            if (activity.getActivityCode().equals(activityCode)) {
                for (Map<String, Object> questionAnswer : activity.getQuestionsAnswers()) {
                    if (questionAnswer.containsKey(questionStableId)) {
                        questionAnswer.replace(ESObjectConstants.ANSWER, value);
                        questionAnswer.replace(questionStableId, value);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public Optional<Address> getAddress() {
        return Optional.ofNullable(address);
    }

    public List<Object> getMedicalProviders() {
        return medicalProviders == null ? Collections.emptyList() : medicalProviders;
    }

    /**
     * When not null, this is the participantId used to query
     * elastic search when this object was created.
     */
    public String getQueriedParticipantId() {
        return queriedParticipantId;
    }

    public List<Object> getInvitations() {
        return invitations == null ? Collections.emptyList() : invitations;
    }

    public List<Activities> getActivities() {
        return activities == null ? Collections.emptyList() : activities;
    }

    public Optional<Long> getStatusTimestamp() {
        return Optional.ofNullable(statusTimestamp);
    }

    public Optional<Profile> getProfile() {
        return Optional.ofNullable(profile);
    }

    public List<Files> getFiles() {
        return files == null ? Collections.emptyList() : files;
    }

    public List<String> getProxies() {
        return proxies == null ? Collections.emptyList() : proxies;
    }

    public List<Map<String, Object>> getWorkflows() {
        return workflows == null ? Collections.emptyList() : workflows;
    }

    public Optional<String> getStatus() {
        return Optional.ofNullable(status);
    }

    public Optional<Dsm> getDsm() {
        return Optional.ofNullable(dsm);
    }

    @JsonGetter("dsm")
    private Dsm dsm() {
        return dsm;
    }

    public Optional<ESComputed> getComputed() {
        return Optional.ofNullable(computed);
    }

    public List<String> getGovernedUsers() {
        return governedUsers == null ? Collections.emptyList() : governedUsers;
    }


    /**
     * Returns the participant id by checking the profile
     * for guid and altpid, preferentially returning the guid.
     */
    public String getParticipantId() {
        return getProfile().map(esProfile -> StringUtils.isNotBlank(esProfile.getGuid())
                        ? esProfile.getGuid()
                        : esProfile.getLegacyAltPid())
                .orElse(StringUtils.EMPTY);
    }

    /**
     * checks if the answer provided by a participant to a survey question matches the criteria
     *
     * @param activityName      stable id of the activity
     * @param question          stable id of the question
     * @param expectedAnswer    desired answer
     */
    public boolean checkAnswerToActivity(String activityName, String question, Object expectedAnswer) {
        Optional<Activities> maybeActivity = this.getActivities().stream().filter(activity -> activity.getActivityCode()
                .equals(activityName)).findAny();
        Optional<Object> possibleAnswer = maybeActivity.isPresent() ? maybeActivity.get().getAnswerToQuestion(question) : Optional.empty();
        return possibleAnswer.isPresent() && possibleAnswer.get().equals(expectedAnswer);

    }

    /**
     * checks if the answer provided by a participant to a survey question matches the criteria
     *
     * @param activityName stable id of the activity
     * @param question     stable id of the question
     */
    public Optional<Object> getParticipantAnswerInSurvey(String activityName, String question) {
        Optional<Activities> maybeActivity = this.getActivities().stream().filter(activity -> activity.getActivityCode()
                .equals(activityName)).findAny();
        return maybeActivity.isPresent() ? maybeActivity.get().getAnswerToQuestion(question) : Optional.empty();
    }

    public String getParticipantGender(String realm, String ddpParticipantId) {
        String participantId = this.getParticipantId();
        if (StringUtils.isBlank(participantId)) {
            throw new DsmInternalError(String.format("The participant %s is missing participant id", ddpParticipantId));
        }
        // if gender is set on tissue page use that
        List<String> list = new ArrayList<>();
        list.add(participantId);
        Map<String, List<OncHistoryDetail>> oncHistoryDetails = OncHistoryDetail.getOncHistoryDetailsByParticipantIds(realm, list);
        if (!oncHistoryDetails.isEmpty()) {
            Optional<OncHistoryDetail> oncHistoryWithGender = oncHistoryDetails.get(participantId).stream()
                    .filter(o -> StringUtils.isNotBlank(o.getGender())).findFirst();
            if (oncHistoryWithGender.isPresent()) {
                return oncHistoryWithGender.get().getGender();
            }
        }
        log.info("Participant {} did not have gender on tissue pages, will look into activities", this.getParticipantId());
        //if gender is not set on tissue page get answer from "ABOUT_YOU.ASSIGNED_SEX"
        return getGenderFromActivities(this.getActivities());
    }

    private String getGenderFromActivities(List<Activities> activities) {
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

    public static class Builder {
        private Address address;
        private List<Object> medicalProviders;
        private List<Object> invitations;
        private ESComputed computed;
        private List<Activities> activities;
        private List<String> governedUsers;
        private Long statusTimeStamp;
        private Profile profile;
        private List<Files> files;
        private List<String> proxies;
        private List<Map<String, Object>> workflows;
        private String status;
        private Dsm dsm;
        private String queriedParticipantId;

        public Builder() {
        }

        public Builder withAddress(Address address) {
            this.address = address;
            return this;
        }

        public Builder withMedicalProviders(List<Object> medicalProviders) {
            this.medicalProviders = medicalProviders;
            return this;
        }

        public Builder withInvitations(List<Object> invitations) {
            this.invitations = invitations;
            return this;
        }

        public Builder withActivities(List<Activities> activities) {
            this.activities = activities;
            return this;
        }

        /**
         * Sets the queried participant id.  Used only for troubleshooting.
         */
        public Builder withQueriedParticipantId(String queriedParticipantId) {
            this.queriedParticipantId = queriedParticipantId;
            return this;
        }

        public Builder withStatusTimeStamp(Long statusTimeStamp) {
            this.statusTimeStamp = statusTimeStamp;
            return this;
        }

        public Builder withProfile(Profile profile) {
            this.profile = profile;
            return this;
        }

        public Builder withFiles(List<Files> files) {
            this.files = files;
            return this;
        }

        public Builder withProxies(List<String> proxies) {
            this.proxies = proxies;
            return this;
        }

        public Builder withWorkFlows(List<Map<String, Object>> workflows) {
            this.workflows = workflows;
            return this;
        }

        public Builder withStatus(String status) {
            this.status = status;
            return this;
        }

        public Builder withDsm(Dsm dsm) {
            this.dsm = dsm;
            return this;
        }

        public Builder withComputed(ESComputed computed) {
            this.computed = computed;
            return this;
        }

        public Builder withGovernedUsers(List<String> governedUsers) {
            this.governedUsers = governedUsers;
            return this;
        }

        public ElasticSearchParticipantDto build() {
            return new ElasticSearchParticipantDto(this);
        }
    }


}
