package org.broadinstitute.ddp.model.migration;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public final class FollowupConsentSurvey implements Gen2Survey {
    @SerializedName("consent_blood")
    private Integer consentBlood;

    @SerializedName("consent_tissue")
    private Integer consentTissue;

    @SerializedName("fullname")
    private String fullname;

    @SerializedName("consent_dob")
    private String consentDob;

    @SerializedName("ddp_created")
    private String ddpCreated;

    @SerializedName("ddp_lastupdated")
    private String ddpLastupdated;

    @SerializedName("ddp_firstcompleted")
    private String ddpFirstcompleted;

    @SerializedName("ddp_participant_shortid")
    private String ddpParticipantShortid;

    @SerializedName("ddp_followup")
    private String ddpFollowup;

    @SerializedName("datstat.submissionid")
    private Integer datstatSubmissionid;

    @SerializedName("datstat.sessionid")
    private String datstatSessionid;

    @SerializedName("datstat.submissionstatus")
    private Integer datstatSubmissionstatus;

    @SerializedName("surveyversion")
    private String surveyversion;
}
