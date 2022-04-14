package org.broadinstitute.ddp.model.migration;

import com.google.gson.annotations.SerializedName;

public class FollowupConsentSurvey implements Gen2Survey {

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

    public Integer getConsentBlood() {
        return consentBlood;
    }

    public void setConsentBlood(Integer consentBlood) {
        this.consentBlood = consentBlood;
    }

    public Integer getConsentTissue() {
        return consentTissue;
    }

    public void setConsentTissue(Integer consentTissue) {
        this.consentTissue = consentTissue;
    }

    public String getFullname() {
        return fullname;
    }

    public void setFullname(String fullname) {
        this.fullname = fullname;
    }

    public String getConsentDob() {
        return consentDob;
    }

    public void setConsentDob(String consentDob) {
        this.consentDob = consentDob;
    }

    public String getDdpCreated() {
        return ddpCreated;
    }

    public void setDdpCreated(String ddpCreated) {
        this.ddpCreated = ddpCreated;
    }

    public String getDdpLastupdated() {
        return ddpLastupdated;
    }

    public void setDdpLastupdated(String ddpLastupdated) {
        this.ddpLastupdated = ddpLastupdated;
    }

    public String getDdpFirstcompleted() {
        return ddpFirstcompleted;
    }

    public void setDdpFirstcompleted(String ddpFirstcompleted) {
        this.ddpFirstcompleted = ddpFirstcompleted;
    }

    public String getDdpFollowup() {
        return ddpFollowup;
    }

    public void setDdpFollowup(String ddpFollowup) {
        this.ddpFollowup = ddpFollowup;
    }

    public Integer getDatstatSubmissionid() {
        return datstatSubmissionid;
    }

    public void setDatstatSubmissionid(Integer datstatSubmissionid) {
        this.datstatSubmissionid = datstatSubmissionid;
    }

    public String getDatstatSessionid() {
        return datstatSessionid;
    }

    public void setDatstatSessionid(String datstatSessionid) {
        this.datstatSessionid = datstatSessionid;
    }

    public Integer getDatstatSubmissionstatus() {
        return datstatSubmissionstatus;
    }

    public void setDatstatSubmissionstatus(Integer datstatSubmissionstatus) {
        this.datstatSubmissionstatus = datstatSubmissionstatus;
    }

    public String getSurveyversion() {
        return surveyversion;
    }

    public void setSurveyversion(String surveyversion) {
        this.surveyversion = surveyversion;
    }

}
