package org.broadinstitute.ddp.model.migration;

import java.util.List;

import com.google.gson.annotations.SerializedName;

public class AboutYouSurvey implements Gen2Survey {

    @SerializedName("diagnosis_date_month")
    private Integer diagnosisDateMonth;

    @SerializedName("diagnosis_date_year")
    private Integer diagnosisDateYear;

    @SerializedName("diagnosis_primary_loc.headneck")
    private Integer diagnosisPrimaryLocHeadneck;

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

    @SerializedName("diagnosis_spread.yes")
    private Integer diagnosisSpreadYes;

    @SerializedName("diagnosis_spread.no")
    private Integer diagnosisSpreadNo;

    @SerializedName("diagnosis_spread.dk")
    private Integer diagnosisSpreadDk;

    @SerializedName("diagnosis_spread_loc.headneck")
    private Integer diagnosisSpreadLocHeadneck;

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

    @SerializedName("post_diagnosis_spread.yes")
    private Integer postDiagnosisSpreadYes;

    @SerializedName("post_diagnosis_spread.no")
    private Integer postDiagnosisSpreadNo;

    @SerializedName("post_diagnosis_spread.dk")
    private Integer postDiagnosisSpreadDk;

    @SerializedName("post_diagnosis_spread_loc.headneck")
    private Integer postDiagnosisSpreadLocHeadneck;

    @SerializedName("post_diagnosis_spread_loc.scalp")
    private Integer postDiagnosisSpreadLocScalp;

    @SerializedName("post_diagnosis_spread_loc.breast")
    private Integer postDiagnosisSpreadLocBreast;

    @SerializedName("post_diagnosis_spread_loc.heart")
    private Integer postDiagnosisSpreadLocHeart;

    @SerializedName("post_diagnosis_spread_loc.liver")
    private Integer postDiagnosisSpreadLocLiver;

    @SerializedName("post_diagnosis_spread_loc.spleen")
    private Integer postDiagnosisSpreadLocSpleen;

    @SerializedName("post_diagnosis_spread_loc.lung")
    private Integer postDiagnosisSpreadLocLung;

    @SerializedName("post_diagnosis_spread_loc.brain")
    private Integer postDiagnosisSpreadLocBrain;

    @SerializedName("post_diagnosis_spread_loc.lymph")
    private Integer postDiagnosisSpreadLocLymph;

    @SerializedName("post_diagnosis_spread_loc.bonelimb")
    private Integer postDiagnosisSpreadLocBonelimb;

    @SerializedName("post_diagnosis_spread_loc.bonelimb.bonelimb_text")
    private String postDiagnosisSpreadLocBonelimbBonelimbText;

    @SerializedName("post_diagnosis_spread_loc.abdominal")
    private Integer postDiagnosisSpreadLocAbdominal;

    @SerializedName("post_diagnosis_spread_loc.abdominal.abdominal_text")
    private String postDiagnosisSpreadLocAbdominalAbdominalText;

    @SerializedName("post_diagnosis_spread_loc.other")
    private Integer postDiagnosisSpreadLocOther;

    @SerializedName("post_diagnosis_spread_loc.other.other_text")
    private String postDiagnosisSpreadLocOtherOtherText;

    @SerializedName("local_recurrence.yes")
    private Integer localRecurrenceYes;

    @SerializedName("local_recurrence.no")
    private Integer localRecurrenceNo;

    @SerializedName("local_recurrence.dk")
    private Integer localRecurrenceDk;

    @SerializedName("local_recurrence_loc.headneck")
    private Integer localRecurrenceLocHeadneck;

    @SerializedName("local_recurrence_loc.scalp")
    private Integer localRecurrenceLocScalp;

    @SerializedName("local_recurrence_loc.breast")
    private Integer localRecurrenceLocBreast;

    @SerializedName("local_recurrence_loc.heart")
    private Integer localRecurrenceLocHeart;

    @SerializedName("local_recurrence_loc.liver")
    private Integer localRecurrenceLocLiver;

    @SerializedName("local_recurrence_loc.spleen")
    private Integer localRecurrenceLocSpleen;

    @SerializedName("local_recurrence_loc.lung")
    private Integer localRecurrenceLocLung;

    @SerializedName("local_recurrence_loc.brain")
    private Integer localRecurrenceLocBrain;

    @SerializedName("local_recurrence_loc.lymph")
    private Integer localRecurrenceLocLymph;

    @SerializedName("local_recurrence_loc.bonelimb")
    private Integer localRecurrenceLocBonelimb;

    @SerializedName("local_recurrence_loc.bonelimb.bonelimb_text")
    private String localRecurrenceLocBonelimbBonelimbText;

    @SerializedName("local_recurrence_loc.abdominal")
    private Integer localRecurrenceLocAbdominal;

    @SerializedName("local_recurrence_loc.abdominal.abdominal_text")
    private String localRecurrenceLocAbdominalAbdominalText;

    @SerializedName("local_recurrence_loc.other")
    private Integer localRecurrenceLocOther;

    @SerializedName("local_recurrence_loc.other.other_text")
    private String localRecurrenceLocOtherOtherText;

    @SerializedName("diagnosis_loc.headfaceneck")
    private Integer diagnosisLocHeadfaceneck;

    @SerializedName("diagnosis_loc.scalp")
    private Integer diagnosisLocScalp;

    @SerializedName("diagnosis_loc.breast")
    private Integer diagnosisLocBreast;

    @SerializedName("diagnosis_loc.heart")
    private Integer diagnosisLocHeart;

    @SerializedName("diagnosis_loc.liver")
    private Integer diagnosisLocLiver;

    @SerializedName("diagnosis_loc.spleen")
    private Integer diagnosisLocSpleen;

    @SerializedName("diagnosis_loc.lung")
    private Integer diagnosisLocLung;

    @SerializedName("diagnosis_loc.brain")
    private Integer diagnosisLocBrain;

    @SerializedName("diagnosis_loc.lymph")
    private Integer diagnosisLocLymph;

    @SerializedName("diagnosis_loc.bonelimb")
    private Integer diagnosisLocBonelimb;

    @SerializedName("diagnosis_loc.bonelimb.bonelimb_text")
    private String diagnosisLocBonelimbBonelimbText;

    @SerializedName("diagnosis_loc.abdominal")
    private Integer diagnosisLocAbdominal;

    @SerializedName("diagnosis_loc.abdominal.abdominal_text")
    private String diagnosisLocAbdominalAbdominalText;

    @SerializedName("diagnosis_loc.other")
    private Integer diagnosisLocOther;

    @SerializedName("diagnosis_loc.other.other_text")
    private String diagnosisLocOtherOtherText;

    @SerializedName("diagnosis_loc.dk")
    private Integer diagnosisLocDk;

    @SerializedName("ever_location.headfaceneck")
    private Integer everLocationHeadfaceneck;

    @SerializedName("ever_location.scalp")
    private Integer everLocationScalp;

    @SerializedName("ever_location.breast")
    private Integer everLocationBreast;

    @SerializedName("ever_location.heart")
    private Integer everLocationHeart;

    @SerializedName("ever_location.liver")
    private Integer everLocationLiver;

    @SerializedName("ever_location.spleen")
    private Integer everLocationSpleen;

    @SerializedName("ever_location.lung")
    private Integer everLocationLung;

    @SerializedName("ever_location.brain")
    private Integer everLocationBrain;

    @SerializedName("ever_location.lymph")
    private Integer everLocationLymph;

    @SerializedName("ever_location.bonelimb")
    private Integer everLocationBonelimb;

    @SerializedName("ever_location.bonelimb.bonelimb_text")
    private String everLocationBonelimbBonelimbText;

    @SerializedName("ever_location.abdominal")
    private Integer everLocationAbdominal;

    @SerializedName("ever_location.abdominal.abdominal_text")
    private String everLocationAbdominalAbdominalText;

    @SerializedName("ever_location.other")
    private Integer everLocationOther;

    @SerializedName("ever_location.other.other_text")
    private String everLocationOtherOtherText;

    @SerializedName("ever_location.dk")
    private Integer everLocationDk;

    @SerializedName("current_location.headfaceneck")
    private Integer currentLocationHeadfaceneck;

    @SerializedName("current_location.scalp")
    private Integer currentLocationScalp;

    @SerializedName("current_location.breast")
    private Integer currentLocationBreast;

    @SerializedName("current_location.heart")
    private Integer currentLocationHeart;

    @SerializedName("current_location.liver")
    private Integer currentLocationLiver;

    @SerializedName("current_location.spleen")
    private Integer currentLocationSpleen;

    @SerializedName("current_location.lung")
    private Integer currentLocationLung;

    @SerializedName("current_location.brain")
    private Integer currentLocationBrain;

    @SerializedName("current_location.lymph")
    private Integer currentLocationLymph;

    @SerializedName("current_location.bonelimb")
    private Integer currentLocationBonelimb;

    @SerializedName("current_location.bonelimb.bonelimb_text")
    private String currentLocationBonelimbBonelimbText;

    @SerializedName("current_location.abdominal")
    private Integer currentLocationAbdominal;

    @SerializedName("current_location.abdominal.abdominal_text")
    private String currentLocationAbdominalAbdominalText;

    @SerializedName("current_location.other")
    private Integer currentLocationOther;

    @SerializedName("current_location.other.other_text")
    private String currentLocationOtherOtherText;

    @SerializedName("current_location.dk")
    private Integer currentLocationDk;

    @SerializedName("current_location.ned")
    private Integer currentLocationNed;

    @SerializedName("surgery.yes")
    private Integer surgeryYes;

    @SerializedName("surgery.no")
    private Integer surgeryNo;

    @SerializedName("surgery.dk")
    private Integer surgeryDk;

    @SerializedName("surgery_clean_margins.yes")
    private Integer surgeryCleanMarginsYes;

    @SerializedName("surgery_clean_margins.no")
    private Integer surgeryCleanMarginsNo;

    @SerializedName("surgery_clean_margins.dk")
    private Integer surgeryCleanMarginsDk;

    @SerializedName("radiation.yes")
    private Integer radiationYes;

    @SerializedName("radiation.no")
    private Integer radiationNo;

    @SerializedName("radiation.dk")
    private Integer radiationDk;

    @SerializedName("radiation_surgery.before")
    private Integer radiationSurgeryBefore;

    @SerializedName("radiation_surgery.after")
    private Integer radiationSurgeryAfter;

    @SerializedName("radiation_surgery.both")
    private Integer radiationSurgeryBoth;

    @SerializedName("radiation_surgery.dk")
    private Integer radiationSurgeryDk;

    @SerializedName("treatment_past")
    private String treatmentPast;

    @SerializedName("treatment_now")
    private String treatmentNow;

    @SerializedName("all_treatment")
    private String allTreatment;

    @SerializedName("currently_treated.yes")
    private Integer currentlyTreatedYes;

    @SerializedName("currently_treated.no")
    private Integer currentlyTreatedNo;

    @SerializedName("currently_treated.dk")
    private Integer currentlyTreatedDk;

    @SerializedName("current_therapy")
    private String currentTherapy;

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

    @SerializedName("other_cancer_list")
    private List<OtherCancer> otherCancerList = null;

    @SerializedName("other_cancer_radiation_loc")
    private String otherCancerRadiationLoc;

    @SerializedName("disease_free_now.yes")
    private Integer diseaseFreeNowYes;

    @SerializedName("disease_free_now.no")
    private Integer diseaseFreeNowNo;

    @SerializedName("disease_free_now.dk")
    private Integer diseaseFreeNowDk;

    @SerializedName("support_membership.yes")
    private Integer supportMembershipYes;

    @SerializedName("support_membership.no")
    private Integer supportMembershipNo;

    @SerializedName("support_membership.dk")
    private Integer supportMembershipDk;

    @SerializedName("support_membership_text")
    private String supportMembershipText;

    @SerializedName("experience_text")
    private String experienceText;

    @SerializedName("hispanic.yes")
    private Integer hispanicYes;

    @SerializedName("hispanic.no")
    private Integer hispanicNo;

    @SerializedName("hispanic.dk")
    private Integer hispanicDk;

    @SerializedName("race.american_indian")
    private Integer raceAmericanIndian;

    @SerializedName("race.japanese")
    private Integer raceJapanese;

    @SerializedName("race.chinese")
    private Integer raceChinese;

    @SerializedName("race.other_east_asian")
    private Integer raceOtherEastAsian;

    @SerializedName("race.south_east_asian")
    private Integer raceSouthEastAsian;

    @SerializedName("race.black")
    private Integer raceBlack;

    @SerializedName("race.native_hawaiian")
    private Integer raceNativeHawaiian;

    @SerializedName("race.white")
    private Integer raceWhite;

    @SerializedName("race.no_answer")
    private Integer raceNoAnswer;

    @SerializedName("race.other")
    private Integer raceOther;

    @SerializedName("race.other.other_text")
    private String raceOtherOtherText;

    @SerializedName("referral_source")
    private String referralSource;

    @SerializedName("ddp_created")
    private String ddpCreated;

    @SerializedName("ddp_lastupdated")
    private String ddpLastupdated;

    @SerializedName("ddp_firstcompleted")
    private String ddpFirstcompleted;

    @SerializedName("ddp_participant_shortid")
    private String ddpParticipantShortid;

    @SerializedName("birth_year")
    private Integer birthYear;

    @SerializedName("country")
    private String country;

    @SerializedName("postal_code")
    private String postalCode;

    @SerializedName("datstat.submissionid")
    private Integer datstatSubmissionid;

    @SerializedName("datstat.sessionid")
    private String datstatSessionid;

    @SerializedName("datstat.submissionstatus")
    private Integer datstatSubmissionstatus;

    @SerializedName("surveyversion")
    private String surveyversion;

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

    public Integer getDiagnosisPrimaryLocHeadneck() {
        return diagnosisPrimaryLocHeadneck;
    }

    public void setDiagnosisPrimaryLocHeadneck(Integer diagnosisPrimaryLocHeadneck) {
        this.diagnosisPrimaryLocHeadneck = diagnosisPrimaryLocHeadneck;
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

    public Integer getDiagnosisSpreadYes() {
        return diagnosisSpreadYes;
    }

    public void setDiagnosisSpreadYes(Integer diagnosisSpreadYes) {
        this.diagnosisSpreadYes = diagnosisSpreadYes;
    }

    public Integer getDiagnosisSpreadNo() {
        return diagnosisSpreadNo;
    }

    public void setDiagnosisSpreadNo(Integer diagnosisSpreadNo) {
        this.diagnosisSpreadNo = diagnosisSpreadNo;
    }

    public Integer getDiagnosisSpreadDk() {
        return diagnosisSpreadDk;
    }

    public void setDiagnosisSpreadDk(Integer diagnosisSpreadDk) {
        this.diagnosisSpreadDk = diagnosisSpreadDk;
    }

    public Integer getDiagnosisSpreadLocHeadneck() {
        return diagnosisSpreadLocHeadneck;
    }

    public void setDiagnosisSpreadLocHeadneck(Integer diagnosisSpreadLocHeadneck) {
        this.diagnosisSpreadLocHeadneck = diagnosisSpreadLocHeadneck;
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

    public Integer getPostDiagnosisSpreadYes() {
        return postDiagnosisSpreadYes;
    }

    public void setPostDiagnosisSpreadYes(Integer postDiagnosisSpreadYes) {
        this.postDiagnosisSpreadYes = postDiagnosisSpreadYes;
    }

    public Integer getPostDiagnosisSpreadNo() {
        return postDiagnosisSpreadNo;
    }

    public void setPostDiagnosisSpreadNo(Integer postDiagnosisSpreadNo) {
        this.postDiagnosisSpreadNo = postDiagnosisSpreadNo;
    }

    public Integer getPostDiagnosisSpreadDk() {
        return postDiagnosisSpreadDk;
    }

    public void setPostDiagnosisSpreadDk(Integer postDiagnosisSpreadDk) {
        this.postDiagnosisSpreadDk = postDiagnosisSpreadDk;
    }

    public Integer getPostDiagnosisSpreadLocHeadneck() {
        return postDiagnosisSpreadLocHeadneck;
    }

    public void setPostDiagnosisSpreadLocHeadneck(Integer postDiagnosisSpreadLocHeadneck) {
        this.postDiagnosisSpreadLocHeadneck = postDiagnosisSpreadLocHeadneck;
    }

    public Integer getPostDiagnosisSpreadLocScalp() {
        return postDiagnosisSpreadLocScalp;
    }

    public void setPostDiagnosisSpreadLocScalp(Integer postDiagnosisSpreadLocScalp) {
        this.postDiagnosisSpreadLocScalp = postDiagnosisSpreadLocScalp;
    }

    public Integer getPostDiagnosisSpreadLocBreast() {
        return postDiagnosisSpreadLocBreast;
    }

    public void setPostDiagnosisSpreadLocBreast(Integer postDiagnosisSpreadLocBreast) {
        this.postDiagnosisSpreadLocBreast = postDiagnosisSpreadLocBreast;
    }

    public Integer getPostDiagnosisSpreadLocHeart() {
        return postDiagnosisSpreadLocHeart;
    }

    public void setPostDiagnosisSpreadLocHeart(Integer postDiagnosisSpreadLocHeart) {
        this.postDiagnosisSpreadLocHeart = postDiagnosisSpreadLocHeart;
    }

    public Integer getPostDiagnosisSpreadLocLiver() {
        return postDiagnosisSpreadLocLiver;
    }

    public void setPostDiagnosisSpreadLocLiver(Integer postDiagnosisSpreadLocLiver) {
        this.postDiagnosisSpreadLocLiver = postDiagnosisSpreadLocLiver;
    }

    public Integer getPostDiagnosisSpreadLocSpleen() {
        return postDiagnosisSpreadLocSpleen;
    }

    public void setPostDiagnosisSpreadLocSpleen(Integer postDiagnosisSpreadLocSpleen) {
        this.postDiagnosisSpreadLocSpleen = postDiagnosisSpreadLocSpleen;
    }

    public Integer getPostDiagnosisSpreadLocLung() {
        return postDiagnosisSpreadLocLung;
    }

    public void setPostDiagnosisSpreadLocLung(Integer postDiagnosisSpreadLocLung) {
        this.postDiagnosisSpreadLocLung = postDiagnosisSpreadLocLung;
    }

    public Integer getPostDiagnosisSpreadLocBrain() {
        return postDiagnosisSpreadLocBrain;
    }

    public void setPostDiagnosisSpreadLocBrain(Integer postDiagnosisSpreadLocBrain) {
        this.postDiagnosisSpreadLocBrain = postDiagnosisSpreadLocBrain;
    }

    public Integer getPostDiagnosisSpreadLocLymph() {
        return postDiagnosisSpreadLocLymph;
    }

    public void setPostDiagnosisSpreadLocLymph(Integer postDiagnosisSpreadLocLymph) {
        this.postDiagnosisSpreadLocLymph = postDiagnosisSpreadLocLymph;
    }

    public Integer getPostDiagnosisSpreadLocBonelimb() {
        return postDiagnosisSpreadLocBonelimb;
    }

    public void setPostDiagnosisSpreadLocBonelimb(Integer postDiagnosisSpreadLocBonelimb) {
        this.postDiagnosisSpreadLocBonelimb = postDiagnosisSpreadLocBonelimb;
    }

    public String getPostDiagnosisSpreadLocBonelimbBonelimbText() {
        return postDiagnosisSpreadLocBonelimbBonelimbText;
    }

    public void setPostDiagnosisSpreadLocBonelimbBonelimbText(String postDiagnosisSpreadLocBonelimbBonelimbText) {
        this.postDiagnosisSpreadLocBonelimbBonelimbText = postDiagnosisSpreadLocBonelimbBonelimbText;
    }

    public Integer getPostDiagnosisSpreadLocAbdominal() {
        return postDiagnosisSpreadLocAbdominal;
    }

    public void setPostDiagnosisSpreadLocAbdominal(Integer postDiagnosisSpreadLocAbdominal) {
        this.postDiagnosisSpreadLocAbdominal = postDiagnosisSpreadLocAbdominal;
    }

    public String getPostDiagnosisSpreadLocAbdominalAbdominalText() {
        return postDiagnosisSpreadLocAbdominalAbdominalText;
    }

    public void setPostDiagnosisSpreadLocAbdominalAbdominalText(String postDiagnosisSpreadLocAbdominalAbdominalText) {
        this.postDiagnosisSpreadLocAbdominalAbdominalText = postDiagnosisSpreadLocAbdominalAbdominalText;
    }

    public Integer getPostDiagnosisSpreadLocOther() {
        return postDiagnosisSpreadLocOther;
    }

    public void setPostDiagnosisSpreadLocOther(Integer postDiagnosisSpreadLocOther) {
        this.postDiagnosisSpreadLocOther = postDiagnosisSpreadLocOther;
    }

    public String getPostDiagnosisSpreadLocOtherOtherText() {
        return postDiagnosisSpreadLocOtherOtherText;
    }

    public void setPostDiagnosisSpreadLocOtherOtherText(String postDiagnosisSpreadLocOtherOtherText) {
        this.postDiagnosisSpreadLocOtherOtherText = postDiagnosisSpreadLocOtherOtherText;
    }

    public Integer getLocalRecurrenceYes() {
        return localRecurrenceYes;
    }

    public void setLocalRecurrenceYes(Integer localRecurrenceYes) {
        this.localRecurrenceYes = localRecurrenceYes;
    }

    public Integer getLocalRecurrenceNo() {
        return localRecurrenceNo;
    }

    public void setLocalRecurrenceNo(Integer localRecurrenceNo) {
        this.localRecurrenceNo = localRecurrenceNo;
    }

    public Integer getLocalRecurrenceDk() {
        return localRecurrenceDk;
    }

    public void setLocalRecurrenceDk(Integer localRecurrenceDk) {
        this.localRecurrenceDk = localRecurrenceDk;
    }

    public Integer getLocalRecurrenceLocHeadneck() {
        return localRecurrenceLocHeadneck;
    }

    public void setLocalRecurrenceLocHeadneck(Integer localRecurrenceLocHeadneck) {
        this.localRecurrenceLocHeadneck = localRecurrenceLocHeadneck;
    }

    public Integer getLocalRecurrenceLocScalp() {
        return localRecurrenceLocScalp;
    }

    public void setLocalRecurrenceLocScalp(Integer localRecurrenceLocScalp) {
        this.localRecurrenceLocScalp = localRecurrenceLocScalp;
    }

    public Integer getLocalRecurrenceLocBreast() {
        return localRecurrenceLocBreast;
    }

    public void setLocalRecurrenceLocBreast(Integer localRecurrenceLocBreast) {
        this.localRecurrenceLocBreast = localRecurrenceLocBreast;
    }

    public Integer getLocalRecurrenceLocHeart() {
        return localRecurrenceLocHeart;
    }

    public void setLocalRecurrenceLocHeart(Integer localRecurrenceLocHeart) {
        this.localRecurrenceLocHeart = localRecurrenceLocHeart;
    }

    public Integer getLocalRecurrenceLocLiver() {
        return localRecurrenceLocLiver;
    }

    public void setLocalRecurrenceLocLiver(Integer localRecurrenceLocLiver) {
        this.localRecurrenceLocLiver = localRecurrenceLocLiver;
    }

    public Integer getLocalRecurrenceLocSpleen() {
        return localRecurrenceLocSpleen;
    }

    public void setLocalRecurrenceLocSpleen(Integer localRecurrenceLocSpleen) {
        this.localRecurrenceLocSpleen = localRecurrenceLocSpleen;
    }

    public Integer getLocalRecurrenceLocLung() {
        return localRecurrenceLocLung;
    }

    public void setLocalRecurrenceLocLung(Integer localRecurrenceLocLung) {
        this.localRecurrenceLocLung = localRecurrenceLocLung;
    }

    public Integer getLocalRecurrenceLocBrain() {
        return localRecurrenceLocBrain;
    }

    public void setLocalRecurrenceLocBrain(Integer localRecurrenceLocBrain) {
        this.localRecurrenceLocBrain = localRecurrenceLocBrain;
    }

    public Integer getLocalRecurrenceLocLymph() {
        return localRecurrenceLocLymph;
    }

    public void setLocalRecurrenceLocLymph(Integer localRecurrenceLocLymph) {
        this.localRecurrenceLocLymph = localRecurrenceLocLymph;
    }

    public Integer getLocalRecurrenceLocBonelimb() {
        return localRecurrenceLocBonelimb;
    }

    public void setLocalRecurrenceLocBonelimb(Integer localRecurrenceLocBonelimb) {
        this.localRecurrenceLocBonelimb = localRecurrenceLocBonelimb;
    }

    public String getLocalRecurrenceLocBonelimbBonelimbText() {
        return localRecurrenceLocBonelimbBonelimbText;
    }

    public void setLocalRecurrenceLocBonelimbBonelimbText(String localRecurrenceLocBonelimbBonelimbText) {
        this.localRecurrenceLocBonelimbBonelimbText = localRecurrenceLocBonelimbBonelimbText;
    }

    public Integer getLocalRecurrenceLocAbdominal() {
        return localRecurrenceLocAbdominal;
    }

    public void setLocalRecurrenceLocAbdominal(Integer localRecurrenceLocAbdominal) {
        this.localRecurrenceLocAbdominal = localRecurrenceLocAbdominal;
    }

    public String getLocalRecurrenceLocAbdominalAbdominalText() {
        return localRecurrenceLocAbdominalAbdominalText;
    }

    public void setLocalRecurrenceLocAbdominalAbdominalText(String localRecurrenceLocAbdominalAbdominalText) {
        this.localRecurrenceLocAbdominalAbdominalText = localRecurrenceLocAbdominalAbdominalText;
    }

    public Integer getLocalRecurrenceLocOther() {
        return localRecurrenceLocOther;
    }

    public void setLocalRecurrenceLocOther(Integer localRecurrenceLocOther) {
        this.localRecurrenceLocOther = localRecurrenceLocOther;
    }

    public String getLocalRecurrenceLocOtherOtherText() {
        return localRecurrenceLocOtherOtherText;
    }

    public void setLocalRecurrenceLocOtherOtherText(String localRecurrenceLocOtherOtherText) {
        this.localRecurrenceLocOtherOtherText = localRecurrenceLocOtherOtherText;
    }

    public Integer getDiagnosisLocHeadfaceneck() {
        return diagnosisLocHeadfaceneck;
    }

    public void setDiagnosisLocHeadfaceneck(Integer diagnosisLocHeadfaceneck) {
        this.diagnosisLocHeadfaceneck = diagnosisLocHeadfaceneck;
    }

    public Integer getDiagnosisLocScalp() {
        return diagnosisLocScalp;
    }

    public void setDiagnosisLocScalp(Integer diagnosisLocScalp) {
        this.diagnosisLocScalp = diagnosisLocScalp;
    }

    public Integer getDiagnosisLocBreast() {
        return diagnosisLocBreast;
    }

    public void setDiagnosisLocBreast(Integer diagnosisLocBreast) {
        this.diagnosisLocBreast = diagnosisLocBreast;
    }

    public Integer getDiagnosisLocHeart() {
        return diagnosisLocHeart;
    }

    public void setDiagnosisLocHeart(Integer diagnosisLocHeart) {
        this.diagnosisLocHeart = diagnosisLocHeart;
    }

    public Integer getDiagnosisLocLiver() {
        return diagnosisLocLiver;
    }

    public void setDiagnosisLocLiver(Integer diagnosisLocLiver) {
        this.diagnosisLocLiver = diagnosisLocLiver;
    }

    public Integer getDiagnosisLocSpleen() {
        return diagnosisLocSpleen;
    }

    public void setDiagnosisLocSpleen(Integer diagnosisLocSpleen) {
        this.diagnosisLocSpleen = diagnosisLocSpleen;
    }

    public Integer getDiagnosisLocLung() {
        return diagnosisLocLung;
    }

    public void setDiagnosisLocLung(Integer diagnosisLocLung) {
        this.diagnosisLocLung = diagnosisLocLung;
    }

    public Integer getDiagnosisLocBrain() {
        return diagnosisLocBrain;
    }

    public void setDiagnosisLocBrain(Integer diagnosisLocBrain) {
        this.diagnosisLocBrain = diagnosisLocBrain;
    }

    public Integer getDiagnosisLocLymph() {
        return diagnosisLocLymph;
    }

    public void setDiagnosisLocLymph(Integer diagnosisLocLymph) {
        this.diagnosisLocLymph = diagnosisLocLymph;
    }

    public Integer getDiagnosisLocBonelimb() {
        return diagnosisLocBonelimb;
    }

    public void setDiagnosisLocBonelimb(Integer diagnosisLocBonelimb) {
        this.diagnosisLocBonelimb = diagnosisLocBonelimb;
    }

    public String getDiagnosisLocBonelimbBonelimbText() {
        return diagnosisLocBonelimbBonelimbText;
    }

    public void setDiagnosisLocBonelimbBonelimbText(String diagnosisLocBonelimbBonelimbText) {
        this.diagnosisLocBonelimbBonelimbText = diagnosisLocBonelimbBonelimbText;
    }

    public Integer getDiagnosisLocAbdominal() {
        return diagnosisLocAbdominal;
    }

    public void setDiagnosisLocAbdominal(Integer diagnosisLocAbdominal) {
        this.diagnosisLocAbdominal = diagnosisLocAbdominal;
    }

    public String getDiagnosisLocAbdominalAbdominalText() {
        return diagnosisLocAbdominalAbdominalText;
    }

    public void setDiagnosisLocAbdominalAbdominalText(String diagnosisLocAbdominalAbdominalText) {
        this.diagnosisLocAbdominalAbdominalText = diagnosisLocAbdominalAbdominalText;
    }

    public Integer getDiagnosisLocOther() {
        return diagnosisLocOther;
    }

    public void setDiagnosisLocOther(Integer diagnosisLocOther) {
        this.diagnosisLocOther = diagnosisLocOther;
    }

    public String getDiagnosisLocOtherOtherText() {
        return diagnosisLocOtherOtherText;
    }

    public void setDiagnosisLocOtherOtherText(String diagnosisLocOtherOtherText) {
        this.diagnosisLocOtherOtherText = diagnosisLocOtherOtherText;
    }

    public Integer getDiagnosisLocDk() {
        return diagnosisLocDk;
    }

    public void setDiagnosisLocDk(Integer diagnosisLocDk) {
        this.diagnosisLocDk = diagnosisLocDk;
    }

    public Integer getEverLocationHeadfaceneck() {
        return everLocationHeadfaceneck;
    }

    public void setEverLocationHeadfaceneck(Integer everLocationHeadfaceneck) {
        this.everLocationHeadfaceneck = everLocationHeadfaceneck;
    }

    public Integer getEverLocationScalp() {
        return everLocationScalp;
    }

    public void setEverLocationScalp(Integer everLocationScalp) {
        this.everLocationScalp = everLocationScalp;
    }

    public Integer getEverLocationBreast() {
        return everLocationBreast;
    }

    public void setEverLocationBreast(Integer everLocationBreast) {
        this.everLocationBreast = everLocationBreast;
    }

    public Integer getEverLocationHeart() {
        return everLocationHeart;
    }

    public void setEverLocationHeart(Integer everLocationHeart) {
        this.everLocationHeart = everLocationHeart;
    }

    public Integer getEverLocationLiver() {
        return everLocationLiver;
    }

    public void setEverLocationLiver(Integer everLocationLiver) {
        this.everLocationLiver = everLocationLiver;
    }

    public Integer getEverLocationSpleen() {
        return everLocationSpleen;
    }

    public void setEverLocationSpleen(Integer everLocationSpleen) {
        this.everLocationSpleen = everLocationSpleen;
    }

    public Integer getEverLocationLung() {
        return everLocationLung;
    }

    public void setEverLocationLung(Integer everLocationLung) {
        this.everLocationLung = everLocationLung;
    }

    public Integer getEverLocationBrain() {
        return everLocationBrain;
    }

    public void setEverLocationBrain(Integer everLocationBrain) {
        this.everLocationBrain = everLocationBrain;
    }

    public Integer getEverLocationLymph() {
        return everLocationLymph;
    }

    public void setEverLocationLymph(Integer everLocationLymph) {
        this.everLocationLymph = everLocationLymph;
    }

    public Integer getEverLocationBonelimb() {
        return everLocationBonelimb;
    }

    public void setEverLocationBonelimb(Integer everLocationBonelimb) {
        this.everLocationBonelimb = everLocationBonelimb;
    }

    public String getEverLocationBonelimbBonelimbText() {
        return everLocationBonelimbBonelimbText;
    }

    public void setEverLocationBonelimbBonelimbText(String everLocationBonelimbBonelimbText) {
        this.everLocationBonelimbBonelimbText = everLocationBonelimbBonelimbText;
    }

    public Integer getEverLocationAbdominal() {
        return everLocationAbdominal;
    }

    public void setEverLocationAbdominal(Integer everLocationAbdominal) {
        this.everLocationAbdominal = everLocationAbdominal;
    }

    public String getEverLocationAbdominalAbdominalText() {
        return everLocationAbdominalAbdominalText;
    }

    public void setEverLocationAbdominalAbdominalText(String everLocationAbdominalAbdominalText) {
        this.everLocationAbdominalAbdominalText = everLocationAbdominalAbdominalText;
    }

    public Integer getEverLocationOther() {
        return everLocationOther;
    }

    public void setEverLocationOther(Integer everLocationOther) {
        this.everLocationOther = everLocationOther;
    }

    public String getEverLocationOtherOtherText() {
        return everLocationOtherOtherText;
    }

    public void setEverLocationOtherOtherText(String everLocationOtherOtherText) {
        this.everLocationOtherOtherText = everLocationOtherOtherText;
    }

    public Integer getEverLocationDk() {
        return everLocationDk;
    }

    public void setEverLocationDk(Integer everLocationDk) {
        this.everLocationDk = everLocationDk;
    }

    public Integer getCurrentLocationHeadfaceneck() {
        return currentLocationHeadfaceneck;
    }

    public void setCurrentLocationHeadfaceneck(Integer currentLocationHeadfaceneck) {
        this.currentLocationHeadfaceneck = currentLocationHeadfaceneck;
    }

    public Integer getCurrentLocationScalp() {
        return currentLocationScalp;
    }

    public void setCurrentLocationScalp(Integer currentLocationScalp) {
        this.currentLocationScalp = currentLocationScalp;
    }

    public Integer getCurrentLocationBreast() {
        return currentLocationBreast;
    }

    public void setCurrentLocationBreast(Integer currentLocationBreast) {
        this.currentLocationBreast = currentLocationBreast;
    }

    public Integer getCurrentLocationHeart() {
        return currentLocationHeart;
    }

    public void setCurrentLocationHeart(Integer currentLocationHeart) {
        this.currentLocationHeart = currentLocationHeart;
    }

    public Integer getCurrentLocationLiver() {
        return currentLocationLiver;
    }

    public void setCurrentLocationLiver(Integer currentLocationLiver) {
        this.currentLocationLiver = currentLocationLiver;
    }

    public Integer getCurrentLocationSpleen() {
        return currentLocationSpleen;
    }

    public void setCurrentLocationSpleen(Integer currentLocationSpleen) {
        this.currentLocationSpleen = currentLocationSpleen;
    }

    public Integer getCurrentLocationLung() {
        return currentLocationLung;
    }

    public void setCurrentLocationLung(Integer currentLocationLung) {
        this.currentLocationLung = currentLocationLung;
    }

    public Integer getCurrentLocationBrain() {
        return currentLocationBrain;
    }

    public void setCurrentLocationBrain(Integer currentLocationBrain) {
        this.currentLocationBrain = currentLocationBrain;
    }

    public Integer getCurrentLocationLymph() {
        return currentLocationLymph;
    }

    public void setCurrentLocationLymph(Integer currentLocationLymph) {
        this.currentLocationLymph = currentLocationLymph;
    }

    public Integer getCurrentLocationBonelimb() {
        return currentLocationBonelimb;
    }

    public void setCurrentLocationBonelimb(Integer currentLocationBonelimb) {
        this.currentLocationBonelimb = currentLocationBonelimb;
    }

    public String getCurrentLocationBonelimbBonelimbText() {
        return currentLocationBonelimbBonelimbText;
    }

    public void setCurrentLocationBonelimbBonelimbText(String currentLocationBonelimbBonelimbText) {
        this.currentLocationBonelimbBonelimbText = currentLocationBonelimbBonelimbText;
    }

    public Integer getCurrentLocationAbdominal() {
        return currentLocationAbdominal;
    }

    public void setCurrentLocationAbdominal(Integer currentLocationAbdominal) {
        this.currentLocationAbdominal = currentLocationAbdominal;
    }

    public String getCurrentLocationAbdominalAbdominalText() {
        return currentLocationAbdominalAbdominalText;
    }

    public void setCurrentLocationAbdominalAbdominalText(String currentLocationAbdominalAbdominalText) {
        this.currentLocationAbdominalAbdominalText = currentLocationAbdominalAbdominalText;
    }

    public Integer getCurrentLocationOther() {
        return currentLocationOther;
    }

    public void setCurrentLocationOther(Integer currentLocationOther) {
        this.currentLocationOther = currentLocationOther;
    }

    public String getCurrentLocationOtherOtherText() {
        return currentLocationOtherOtherText;
    }

    public void setCurrentLocationOtherOtherText(String currentLocationOtherOtherText) {
        this.currentLocationOtherOtherText = currentLocationOtherOtherText;
    }

    public Integer getCurrentLocationDk() {
        return currentLocationDk;
    }

    public void setCurrentLocationDk(Integer currentLocationDk) {
        this.currentLocationDk = currentLocationDk;
    }

    public Integer getCurrentLocationNed() {
        return currentLocationNed;
    }

    public void setCurrentLocationNed(Integer currentLocationNed) {
        this.currentLocationNed = currentLocationNed;
    }

    public Integer getSurgeryYes() {
        return surgeryYes;
    }

    public void setSurgeryYes(Integer surgeryYes) {
        this.surgeryYes = surgeryYes;
    }

    public Integer getSurgeryNo() {
        return surgeryNo;
    }

    public void setSurgeryNo(Integer surgeryNo) {
        this.surgeryNo = surgeryNo;
    }

    public Integer getSurgeryDk() {
        return surgeryDk;
    }

    public void setSurgeryDk(Integer surgeryDk) {
        this.surgeryDk = surgeryDk;
    }

    public Integer getSurgeryCleanMarginsYes() {
        return surgeryCleanMarginsYes;
    }

    public void setSurgeryCleanMarginsYes(Integer surgeryCleanMarginsYes) {
        this.surgeryCleanMarginsYes = surgeryCleanMarginsYes;
    }

    public Integer getSurgeryCleanMarginsNo() {
        return surgeryCleanMarginsNo;
    }

    public void setSurgeryCleanMarginsNo(Integer surgeryCleanMarginsNo) {
        this.surgeryCleanMarginsNo = surgeryCleanMarginsNo;
    }

    public Integer getSurgeryCleanMarginsDk() {
        return surgeryCleanMarginsDk;
    }

    public void setSurgeryCleanMarginsDk(Integer surgeryCleanMarginsDk) {
        this.surgeryCleanMarginsDk = surgeryCleanMarginsDk;
    }

    public Integer getRadiationYes() {
        return radiationYes;
    }

    public void setRadiationYes(Integer radiationYes) {
        this.radiationYes = radiationYes;
    }

    public Integer getRadiationNo() {
        return radiationNo;
    }

    public void setRadiationNo(Integer radiationNo) {
        this.radiationNo = radiationNo;
    }

    public Integer getRadiationDk() {
        return radiationDk;
    }

    public void setRadiationDk(Integer radiationDk) {
        this.radiationDk = radiationDk;
    }

    public Integer getRadiationSurgeryBefore() {
        return radiationSurgeryBefore;
    }

    public void setRadiationSurgeryBefore(Integer radiationSurgeryBefore) {
        this.radiationSurgeryBefore = radiationSurgeryBefore;
    }

    public Integer getRadiationSurgeryAfter() {
        return radiationSurgeryAfter;
    }

    public void setRadiationSurgeryAfter(Integer radiationSurgeryAfter) {
        this.radiationSurgeryAfter = radiationSurgeryAfter;
    }

    public Integer getRadiationSurgeryBoth() {
        return radiationSurgeryBoth;
    }

    public void setRadiationSurgeryBoth(Integer radiationSurgeryBoth) {
        this.radiationSurgeryBoth = radiationSurgeryBoth;
    }

    public Integer getRadiationSurgeryDk() {
        return radiationSurgeryDk;
    }

    public void setRadiationSurgeryDk(Integer radiationSurgeryDk) {
        this.radiationSurgeryDk = radiationSurgeryDk;
    }

    public String getTreatmentPast() {
        return treatmentPast;
    }

    public void setTreatmentPast(String treatmentPast) {
        this.treatmentPast = treatmentPast;
    }

    public String getTreatmentNow() {
        return treatmentNow;
    }

    public void setTreatmentNow(String treatmentNow) {
        this.treatmentNow = treatmentNow;
    }

    public String getAllTreatment() {
        return allTreatment;
    }

    public void setAllTreatment(String allTreatment) {
        this.allTreatment = allTreatment;
    }

    public Integer getCurrentlyTreatedYes() {
        return currentlyTreatedYes;
    }

    public void setCurrentlyTreatedYes(Integer currentlyTreatedYes) {
        this.currentlyTreatedYes = currentlyTreatedYes;
    }

    public Integer getCurrentlyTreatedNo() {
        return currentlyTreatedNo;
    }

    public void setCurrentlyTreatedNo(Integer currentlyTreatedNo) {
        this.currentlyTreatedNo = currentlyTreatedNo;
    }

    public Integer getCurrentlyTreatedDk() {
        return currentlyTreatedDk;
    }

    public void setCurrentlyTreatedDk(Integer currentlyTreatedDk) {
        this.currentlyTreatedDk = currentlyTreatedDk;
    }

    public String getCurrentTherapy() {
        return currentTherapy;
    }

    public void setCurrentTherapy(String currentTherapy) {
        this.currentTherapy = currentTherapy;
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

    public List<OtherCancer> getOtherCancerList() {
        return otherCancerList;
    }

    public void setOtherCancerList(List<OtherCancer> otherCancerList) {
        this.otherCancerList = otherCancerList;
    }

    public String getOtherCancerRadiationLoc() {
        return otherCancerRadiationLoc;
    }

    public void setOtherCancerRadiationLoc(String otherCancerRadiationLoc) {
        this.otherCancerRadiationLoc = otherCancerRadiationLoc;
    }

    public Integer getDiseaseFreeNowYes() {
        return diseaseFreeNowYes;
    }

    public void setDiseaseFreeNowYes(Integer diseaseFreeNowYes) {
        this.diseaseFreeNowYes = diseaseFreeNowYes;
    }

    public Integer getDiseaseFreeNowNo() {
        return diseaseFreeNowNo;
    }

    public void setDiseaseFreeNowNo(Integer diseaseFreeNowNo) {
        this.diseaseFreeNowNo = diseaseFreeNowNo;
    }

    public Integer getDiseaseFreeNowDk() {
        return diseaseFreeNowDk;
    }

    public void setDiseaseFreeNowDk(Integer diseaseFreeNowDk) {
        this.diseaseFreeNowDk = diseaseFreeNowDk;
    }

    public Integer getSupportMembershipYes() {
        return supportMembershipYes;
    }

    public void setSupportMembershipYes(Integer supportMembershipYes) {
        this.supportMembershipYes = supportMembershipYes;
    }

    public Integer getSupportMembershipNo() {
        return supportMembershipNo;
    }

    public void setSupportMembershipNo(Integer supportMembershipNo) {
        this.supportMembershipNo = supportMembershipNo;
    }

    public Integer getSupportMembershipDk() {
        return supportMembershipDk;
    }

    public void setSupportMembershipDk(Integer supportMembershipDk) {
        this.supportMembershipDk = supportMembershipDk;
    }

    public String getSupportMembershipText() {
        return supportMembershipText;
    }

    public void setSupportMembershipText(String supportMembershipText) {
        this.supportMembershipText = supportMembershipText;
    }

    public String getExperienceText() {
        return experienceText;
    }

    public void setExperienceText(String experienceText) {
        this.experienceText = experienceText;
    }

    public Integer getHispanicYes() {
        return hispanicYes;
    }

    public void setHispanicYes(Integer hispanicYes) {
        this.hispanicYes = hispanicYes;
    }

    public Integer getHispanicNo() {
        return hispanicNo;
    }

    public void setHispanicNo(Integer hispanicNo) {
        this.hispanicNo = hispanicNo;
    }

    public Integer getHispanicDk() {
        return hispanicDk;
    }

    public void setHispanicDk(Integer hispanicDk) {
        this.hispanicDk = hispanicDk;
    }

    public Integer getRaceAmericanIndian() {
        return raceAmericanIndian;
    }

    public void setRaceAmericanIndian(Integer raceAmericanIndian) {
        this.raceAmericanIndian = raceAmericanIndian;
    }

    public Integer getRaceJapanese() {
        return raceJapanese;
    }

    public void setRaceJapanese(Integer raceJapanese) {
        this.raceJapanese = raceJapanese;
    }

    public Integer getRaceChinese() {
        return raceChinese;
    }

    public void setRaceChinese(Integer raceChinese) {
        this.raceChinese = raceChinese;
    }

    public Integer getRaceOtherEastAsian() {
        return raceOtherEastAsian;
    }

    public void setRaceOtherEastAsian(Integer raceOtherEastAsian) {
        this.raceOtherEastAsian = raceOtherEastAsian;
    }

    public Integer getRaceSouthEastAsian() {
        return raceSouthEastAsian;
    }

    public void setRaceSouthEastAsian(Integer raceSouthEastAsian) {
        this.raceSouthEastAsian = raceSouthEastAsian;
    }

    public Integer getRaceBlack() {
        return raceBlack;
    }

    public void setRaceBlack(Integer raceBlack) {
        this.raceBlack = raceBlack;
    }

    public Integer getRaceNativeHawaiian() {
        return raceNativeHawaiian;
    }

    public void setRaceNativeHawaiian(Integer raceNativeHawaiian) {
        this.raceNativeHawaiian = raceNativeHawaiian;
    }

    public Integer getRaceWhite() {
        return raceWhite;
    }

    public void setRaceWhite(Integer raceWhite) {
        this.raceWhite = raceWhite;
    }

    public Integer getRaceNoAnswer() {
        return raceNoAnswer;
    }

    public void setRaceNoAnswer(Integer raceNoAnswer) {
        this.raceNoAnswer = raceNoAnswer;
    }

    public Integer getRaceOther() {
        return raceOther;
    }

    public void setRaceOther(Integer raceOther) {
        this.raceOther = raceOther;
    }

    public String getRaceOtherOtherText() {
        return raceOtherOtherText;
    }

    public void setRaceOtherOtherText(String raceOtherOtherText) {
        this.raceOtherOtherText = raceOtherOtherText;
    }

    public String getReferralSource() {
        return referralSource;
    }

    public void setReferralSource(String referralSource) {
        this.referralSource = referralSource;
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

    public Integer getBirthYear() {
        return birthYear;
    }

    public void setBirthYear(Integer birthYear) {
        this.birthYear = birthYear;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
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
