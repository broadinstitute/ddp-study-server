package org.broadinstitute.ddp.model.migration;

import com.google.gson.annotations.SerializedName;

public class LovedOneSurvey implements Gen2Survey {
    @SerializedName("source_first_name")
    private String sourceFirstName;
    @SerializedName("source_last_name")
    private String sourceLastName;
    @SerializedName("source_email")
    private String sourceEmail;
    @SerializedName("relation_to")
    private Integer relationTo;
    @SerializedName("relation_to.other_text")
    private String relationToOtherText;
    @SerializedName("first_name")
    private String firstName;
    @SerializedName("last_name")
    private String lastName;
    @SerializedName("dob")
    private String dob;
    @SerializedName("diagnosis_postal_code")
    private String diagnosisPostalCode;
    @SerializedName("passed_postal_code")
    private String passedPostalCode;
    @SerializedName("diagnosis_date_month")
    private Integer diagnosisDateMonth;
    @SerializedName("diagnosis_date_year")
    private Integer diagnosisDateYear;
    @SerializedName("passing_date_month")
    private Integer passingDateMonth;
    @SerializedName("passing_date_year")
    private Integer passingDateYear;
    @SerializedName("diagnosis_primary_loc.headfaceneck")
    private Integer diagnosisPrimaryLocHeadfaceneck;
    @SerializedName("diagnosis_primary_loc.scalp")
    private Integer diagnosisPrimaryLocScalp;
    @SerializedName("diagnosis_primary_loc.breast")
    private Integer diagnosisPrimaryLocBreast;
    @SerializedName("diagnosis_primary_loc.heart")
    private Integer diagnosisPrimaryLocHeart;
    @SerializedName("diagnosis_primary_loc.liver")
    private Integer diagnosisPrimaryLocLiver;
    @SerializedName("diagnosis_primary_loc.spleen")
    private Integer diagnosisPrimaryLocSpleen;
    @SerializedName("diagnosis_primary_loc.lung")
    private Integer diagnosisPrimaryLocLung;
    @SerializedName("diagnosis_primary_loc.brain")
    private Integer diagnosisPrimaryLocBrain;
    @SerializedName("diagnosis_primary_loc.lymph")
    private Integer diagnosisPrimaryLocLymph;
    @SerializedName("diagnosis_primary_loc.bonelimb")
    private Integer diagnosisPrimaryLocBonelimb;
    @SerializedName("diagnosis_primary_loc.bonelimb.bonelimb_text")
    private String diagnosisPrimaryLocBonelimbBonelimbText;
    @SerializedName("diagnosis_primary_loc.abdominal")
    private Integer diagnosisPrimaryLocAbdominal;
    @SerializedName("diagnosis_primary_loc.abdominal.abdominal_text")
    private String diagnosisPrimaryLocAbdominalAbdominalText;
    @SerializedName("diagnosis_primary_loc.other")
    private Integer diagnosisPrimaryLocOther;
    @SerializedName("diagnosis_primary_loc.other.other_text")
    private String diagnosisPrimaryLocOtherOtherText;
    @SerializedName("diagnosis_primary_loc.dk")
    private Integer diagnosisPrimaryLocDk;
    @SerializedName("diagnosis_spread_loc.headfaceneck")
    private Integer diagnosisSpreadLocHeadfaceneck;
    @SerializedName("diagnosis_spread_loc.scalp")
    private Integer diagnosisSpreadLocScalp;
    @SerializedName("diagnosis_spread_loc.breast")
    private Integer diagnosisSpreadLocBreast;
    @SerializedName("diagnosis_spread_loc.heart")
    private Integer diagnosisSpreadLocHeart;
    @SerializedName("diagnosis_spread_loc.liver")
    private Integer diagnosisSpreadLocLiver;
    @SerializedName("diagnosis_spread_loc.spleen")
    private Integer diagnosisSpreadLocSpleen;
    @SerializedName("diagnosis_spread_loc.lung")
    private Integer diagnosisSpreadLocLung;
    @SerializedName("diagnosis_spread_loc.brain")
    private Integer diagnosisSpreadLocBrain;
    @SerializedName("diagnosis_spread_loc.lymph")
    private Integer diagnosisSpreadLocLymph;
    @SerializedName("diagnosis_spread_loc.bonelimb")
    private Integer diagnosisSpreadLocBonelimb;
    @SerializedName("diagnosis_spread_loc.bonelimb.bonelimb_text")
    private String diagnosisSpreadLocBonelimbBonelimbText;
    @SerializedName("diagnosis_spread_loc.abdominal")
    private Integer diagnosisSpreadLocAbdominal;
    @SerializedName("diagnosis_spread_loc.abdominal.abdominal_text")
    private String diagnosisSpreadLocAbdominalAbdominalText;
    @SerializedName("diagnosis_spread_loc.other")
    private Integer diagnosisSpreadLocOther;
    @SerializedName("diagnosis_spread_loc.other.other_text")
    private String diagnosisSpreadLocOtherOtherText;
    @SerializedName("diagnosis_spread_loc.dk")
    private Integer diagnosisSpreadLocDk;
    @SerializedName("other_cancer.yes")
    private Integer otherCancerYes;
    @SerializedName("other_cancer.no")
    private Integer otherCancerNo;
    @SerializedName("other_cancer.dk")
    private Integer otherCancerDk;
    @SerializedName("other_cancer_radiation.yes")
    private Integer otherCancerRadiationYes;
    @SerializedName("other_cancer_radiation.no")
    private Integer otherCancerRadiationNo;
    @SerializedName("other_cancer_radiation.dk")
    private Integer otherCancerRadiationDk;
    @SerializedName("other_cancer_radiation_loc")
    private String otherCancerRadiationLoc;
    @SerializedName("other_cancer_text")
    private String otherCancerText;
    @SerializedName("experience_text")
    private String experienceText;
    @SerializedName("future_contact.yes")
    private Integer futureContactYes;
    @SerializedName("future_contact.no")
    private Integer futureContactNo;
    @SerializedName("future_contact.dk")
    private Integer futureContactDk;
    @SerializedName("ddp_created")
    private String ddpCreated;
    @SerializedName("ddp_lastupdated")
    private String ddpLastupdated;
    @SerializedName("ddp_firstcompleted")
    private String ddpFirstcompleted;
    @SerializedName("ddp_participant_shortid")
    private String ddpParticipantShortid;
    @SerializedName("datstat.submissionid")
    private Integer datstatSubmissionid;
    @SerializedName("datstat.sessionid")
    private String datstatSessionid;
    @SerializedName("datstat.submissionstatus")
    private Integer datstatSubmissionstatus;
    @SerializedName("surveyversion")
    private String surveyversion;

    public String getSourceFirstName() {
        return sourceFirstName;
    }

    public void setSourceFirstName(String sourceFirstName) {
        this.sourceFirstName = sourceFirstName;
    }

    public String getSourceLastName() {
        return sourceLastName;
    }

    public void setSourceLastName(String sourceLastName) {
        this.sourceLastName = sourceLastName;
    }

    public String getSourceEmail() {
        return sourceEmail;
    }

    public void setSourceEmail(String sourceEmail) {
        this.sourceEmail = sourceEmail;
    }

    public String getRelationToOtherText() {
        return relationToOtherText;
    }

    public void setRelationToOtherText(String relationToOtherText) {
        this.relationToOtherText = relationToOtherText;
    }

    public Integer getRelationTo() {
        return relationTo;
    }

    public void setRelationTo(Integer relationTo) {
        this.relationTo = relationTo;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getDob() {
        return dob;
    }

    public void setDob(String dob) {
        this.dob = dob;
    }

    public String getDiagnosisPostalCode() {
        return diagnosisPostalCode;
    }

    public void setDiagnosisPostalCode(String diagnosisPostalCode) {
        this.diagnosisPostalCode = diagnosisPostalCode;
    }

    public String getPassedPostalCode() {
        return passedPostalCode;
    }

    public void setPassedPostalCode(String passedPostalCode) {
        this.passedPostalCode = passedPostalCode;
    }

    public Integer getDiagnosisDateMonth() {
        return diagnosisDateMonth;
    }

    public void setDiagnosisDateMonth(Integer diagnosisDateMonth) {
        this.diagnosisDateMonth = diagnosisDateMonth;
    }

    public Integer getDiagnosisDateYear() {
        return diagnosisDateYear;
    }

    public void setDiagnosisDateYear(Integer diagnosisDateYear) {
        this.diagnosisDateYear = diagnosisDateYear;
    }

    public Integer getPassingDateMonth() {
        return passingDateMonth;
    }

    public void setPassingDateMonth(Integer passingDateMonth) {
        this.passingDateMonth = passingDateMonth;
    }

    public Integer getPassingDateYear() {
        return passingDateYear;
    }

    public void setPassingDateYear(Integer passingDateYear) {
        this.passingDateYear = passingDateYear;
    }

    public Integer getDiagnosisPrimaryLocHeadfaceneck() {
        return diagnosisPrimaryLocHeadfaceneck;
    }

    public void setDiagnosisPrimaryLocHeadfaceneck(Integer diagnosisPrimaryLocHeadfaceneck) {
        this.diagnosisPrimaryLocHeadfaceneck = diagnosisPrimaryLocHeadfaceneck;
    }

    public Integer getDiagnosisPrimaryLocScalp() {
        return diagnosisPrimaryLocScalp;
    }

    public void setDiagnosisPrimaryLocScalp(Integer diagnosisPrimaryLocScalp) {
        this.diagnosisPrimaryLocScalp = diagnosisPrimaryLocScalp;
    }

    public Integer getDiagnosisPrimaryLocBreast() {
        return diagnosisPrimaryLocBreast;
    }

    public void setDiagnosisPrimaryLocBreast(Integer diagnosisPrimaryLocBreast) {
        this.diagnosisPrimaryLocBreast = diagnosisPrimaryLocBreast;
    }

    public Integer getDiagnosisPrimaryLocHeart() {
        return diagnosisPrimaryLocHeart;
    }

    public void setDiagnosisPrimaryLocHeart(Integer diagnosisPrimaryLocHeart) {
        this.diagnosisPrimaryLocHeart = diagnosisPrimaryLocHeart;
    }

    public Integer getDiagnosisPrimaryLocLiver() {
        return diagnosisPrimaryLocLiver;
    }

    public void setDiagnosisPrimaryLocLiver(Integer diagnosisPrimaryLocLiver) {
        this.diagnosisPrimaryLocLiver = diagnosisPrimaryLocLiver;
    }

    public Integer getDiagnosisPrimaryLocSpleen() {
        return diagnosisPrimaryLocSpleen;
    }

    public void setDiagnosisPrimaryLocSpleen(Integer diagnosisPrimaryLocSpleen) {
        this.diagnosisPrimaryLocSpleen = diagnosisPrimaryLocSpleen;
    }

    public Integer getDiagnosisPrimaryLocLung() {
        return diagnosisPrimaryLocLung;
    }

    public void setDiagnosisPrimaryLocLung(Integer diagnosisPrimaryLocLung) {
        this.diagnosisPrimaryLocLung = diagnosisPrimaryLocLung;
    }

    public Integer getDiagnosisPrimaryLocBrain() {
        return diagnosisPrimaryLocBrain;
    }

    public void setDiagnosisPrimaryLocBrain(Integer diagnosisPrimaryLocBrain) {
        this.diagnosisPrimaryLocBrain = diagnosisPrimaryLocBrain;
    }

    public Integer getDiagnosisPrimaryLocLymph() {
        return diagnosisPrimaryLocLymph;
    }

    public void setDiagnosisPrimaryLocLymph(Integer diagnosisPrimaryLocLymph) {
        this.diagnosisPrimaryLocLymph = diagnosisPrimaryLocLymph;
    }

    public Integer getDiagnosisPrimaryLocBonelimb() {
        return diagnosisPrimaryLocBonelimb;
    }

    public void setDiagnosisPrimaryLocBonelimb(Integer diagnosisPrimaryLocBonelimb) {
        this.diagnosisPrimaryLocBonelimb = diagnosisPrimaryLocBonelimb;
    }

    public String getDiagnosisPrimaryLocBonelimbBonelimbText() {
        return diagnosisPrimaryLocBonelimbBonelimbText;
    }

    public void setDiagnosisPrimaryLocBonelimbBonelimbText(String diagnosisPrimaryLocBonelimbBonelimbText) {
        this.diagnosisPrimaryLocBonelimbBonelimbText = diagnosisPrimaryLocBonelimbBonelimbText;
    }

    public Integer getDiagnosisPrimaryLocAbdominal() {
        return diagnosisPrimaryLocAbdominal;
    }

    public void setDiagnosisPrimaryLocAbdominal(Integer diagnosisPrimaryLocAbdominal) {
        this.diagnosisPrimaryLocAbdominal = diagnosisPrimaryLocAbdominal;
    }

    public String getDiagnosisPrimaryLocAbdominalAbdominalText() {
        return diagnosisPrimaryLocAbdominalAbdominalText;
    }

    public void setDiagnosisPrimaryLocAbdominalAbdominalText(String diagnosisPrimaryLocAbdominalAbdominalText) {
        this.diagnosisPrimaryLocAbdominalAbdominalText = diagnosisPrimaryLocAbdominalAbdominalText;
    }

    public Integer getDiagnosisPrimaryLocOther() {
        return diagnosisPrimaryLocOther;
    }

    public void setDiagnosisPrimaryLocOther(Integer diagnosisPrimaryLocOther) {
        this.diagnosisPrimaryLocOther = diagnosisPrimaryLocOther;
    }

    public String getDiagnosisPrimaryLocOtherOtherText() {
        return diagnosisPrimaryLocOtherOtherText;
    }

    public void setDiagnosisPrimaryLocOtherOtherText(String diagnosisPrimaryLocOtherOtherText) {
        this.diagnosisPrimaryLocOtherOtherText = diagnosisPrimaryLocOtherOtherText;
    }

    public Integer getDiagnosisPrimaryLocDk() {
        return diagnosisPrimaryLocDk;
    }

    public void setDiagnosisPrimaryLocDk(Integer diagnosisPrimaryLocDk) {
        this.diagnosisPrimaryLocDk = diagnosisPrimaryLocDk;
    }

    public Integer getDiagnosisSpreadLocHeadfaceneck() {
        return diagnosisSpreadLocHeadfaceneck;
    }

    public void setDiagnosisSpreadLocHeadfaceneck(Integer diagnosisSpreadLocHeadfaceneck) {
        this.diagnosisSpreadLocHeadfaceneck = diagnosisSpreadLocHeadfaceneck;
    }

    public Integer getDiagnosisSpreadLocScalp() {
        return diagnosisSpreadLocScalp;
    }

    public void setDiagnosisSpreadLocScalp(Integer diagnosisSpreadLocScalp) {
        this.diagnosisSpreadLocScalp = diagnosisSpreadLocScalp;
    }

    public Integer getDiagnosisSpreadLocBreast() {
        return diagnosisSpreadLocBreast;
    }

    public void setDiagnosisSpreadLocBreast(Integer diagnosisSpreadLocBreast) {
        this.diagnosisSpreadLocBreast = diagnosisSpreadLocBreast;
    }

    public Integer getDiagnosisSpreadLocHeart() {
        return diagnosisSpreadLocHeart;
    }

    public void setDiagnosisSpreadLocHeart(Integer diagnosisSpreadLocHeart) {
        this.diagnosisSpreadLocHeart = diagnosisSpreadLocHeart;
    }

    public Integer getDiagnosisSpreadLocLiver() {
        return diagnosisSpreadLocLiver;
    }

    public void setDiagnosisSpreadLocLiver(Integer diagnosisSpreadLocLiver) {
        this.diagnosisSpreadLocLiver = diagnosisSpreadLocLiver;
    }

    public Integer getDiagnosisSpreadLocSpleen() {
        return diagnosisSpreadLocSpleen;
    }

    public void setDiagnosisSpreadLocSpleen(Integer diagnosisSpreadLocSpleen) {
        this.diagnosisSpreadLocSpleen = diagnosisSpreadLocSpleen;
    }

    public Integer getDiagnosisSpreadLocLung() {
        return diagnosisSpreadLocLung;
    }

    public void setDiagnosisSpreadLocLung(Integer diagnosisSpreadLocLung) {
        this.diagnosisSpreadLocLung = diagnosisSpreadLocLung;
    }

    public Integer getDiagnosisSpreadLocBrain() {
        return diagnosisSpreadLocBrain;
    }

    public void setDiagnosisSpreadLocBrain(Integer diagnosisSpreadLocBrain) {
        this.diagnosisSpreadLocBrain = diagnosisSpreadLocBrain;
    }

    public Integer getDiagnosisSpreadLocLymph() {
        return diagnosisSpreadLocLymph;
    }

    public void setDiagnosisSpreadLocLymph(Integer diagnosisSpreadLocLymph) {
        this.diagnosisSpreadLocLymph = diagnosisSpreadLocLymph;
    }

    public Integer getDiagnosisSpreadLocBonelimb() {
        return diagnosisSpreadLocBonelimb;
    }

    public void setDiagnosisSpreadLocBonelimb(Integer diagnosisSpreadLocBonelimb) {
        this.diagnosisSpreadLocBonelimb = diagnosisSpreadLocBonelimb;
    }

    public String getDiagnosisSpreadLocBonelimbBonelimbText() {
        return diagnosisSpreadLocBonelimbBonelimbText;
    }

    public void setDiagnosisSpreadLocBonelimbBonelimbText(String diagnosisSpreadLocBonelimbBonelimbText) {
        this.diagnosisSpreadLocBonelimbBonelimbText = diagnosisSpreadLocBonelimbBonelimbText;
    }

    public Integer getDiagnosisSpreadLocAbdominal() {
        return diagnosisSpreadLocAbdominal;
    }

    public void setDiagnosisSpreadLocAbdominal(Integer diagnosisSpreadLocAbdominal) {
        this.diagnosisSpreadLocAbdominal = diagnosisSpreadLocAbdominal;
    }

    public String getDiagnosisSpreadLocAbdominalAbdominalText() {
        return diagnosisSpreadLocAbdominalAbdominalText;
    }

    public void setDiagnosisSpreadLocAbdominalAbdominalText(String diagnosisSpreadLocAbdominalAbdominalText) {
        this.diagnosisSpreadLocAbdominalAbdominalText = diagnosisSpreadLocAbdominalAbdominalText;
    }

    public Integer getDiagnosisSpreadLocOther() {
        return diagnosisSpreadLocOther;
    }

    public void setDiagnosisSpreadLocOther(Integer diagnosisSpreadLocOther) {
        this.diagnosisSpreadLocOther = diagnosisSpreadLocOther;
    }

    public String getDiagnosisSpreadLocOtherOtherText() {
        return diagnosisSpreadLocOtherOtherText;
    }

    public void setDiagnosisSpreadLocOtherOtherText(String diagnosisSpreadLocOtherOtherText) {
        this.diagnosisSpreadLocOtherOtherText = diagnosisSpreadLocOtherOtherText;
    }

    public Integer getDiagnosisSpreadLocDk() {
        return diagnosisSpreadLocDk;
    }

    public void setDiagnosisSpreadLocDk(Integer diagnosisSpreadLocDk) {
        this.diagnosisSpreadLocDk = diagnosisSpreadLocDk;
    }

    public Integer getOtherCancerYes() {
        return otherCancerYes;
    }

    public void setOtherCancerYes(Integer otherCancerYes) {
        this.otherCancerYes = otherCancerYes;
    }

    public Integer getOtherCancerNo() {
        return otherCancerNo;
    }

    public void setOtherCancerNo(Integer otherCancerNo) {
        this.otherCancerNo = otherCancerNo;
    }

    public Integer getOtherCancerDk() {
        return otherCancerDk;
    }

    public void setOtherCancerDk(Integer otherCancerDk) {
        this.otherCancerDk = otherCancerDk;
    }

    public Integer getOtherCancerRadiationYes() {
        return otherCancerRadiationYes;
    }

    public void setOtherCancerRadiationYes(Integer otherCancerRadiationYes) {
        this.otherCancerRadiationYes = otherCancerRadiationYes;
    }

    public Integer getOtherCancerRadiationNo() {
        return otherCancerRadiationNo;
    }

    public void setOtherCancerRadiationNo(Integer otherCancerRadiationNo) {
        this.otherCancerRadiationNo = otherCancerRadiationNo;
    }

    public Integer getOtherCancerRadiationDk() {
        return otherCancerRadiationDk;
    }

    public void setOtherCancerRadiationDk(Integer otherCancerRadiationDk) {
        this.otherCancerRadiationDk = otherCancerRadiationDk;
    }

    public String getOtherCancerRadiationLoc() {
        return otherCancerRadiationLoc;
    }

    public void setOtherCancerRadiationLoc(String otherCancerRadiationLoc) {
        this.otherCancerRadiationLoc = otherCancerRadiationLoc;
    }

    public String getOtherCancerText() {
        return otherCancerText;
    }

    public void setOtherCancerText(String otherCancerText) {
        this.otherCancerText = otherCancerText;
    }

    public String getExperienceText() {
        return experienceText;
    }

    public void setExperienceText(String experienceText) {
        this.experienceText = experienceText;
    }

    public Integer getFutureContactYes() {
        return futureContactYes;
    }

    public void setFutureContactYes(Integer futureContactYes) {
        this.futureContactYes = futureContactYes;
    }

    public Integer getFutureContactNo() {
        return futureContactNo;
    }

    public void setFutureContactNo(Integer futureContactNo) {
        this.futureContactNo = futureContactNo;
    }

    public Integer getFutureContactDk() {
        return futureContactDk;
    }

    public void setFutureContactDk(Integer futureContactDk) {
        this.futureContactDk = futureContactDk;
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

    public String getDdpParticipantShortid() {
        return ddpParticipantShortid;
    }

    public void setDdpParticipantShortid(String ddpParticipantShortid) {
        this.ddpParticipantShortid = ddpParticipantShortid;
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
