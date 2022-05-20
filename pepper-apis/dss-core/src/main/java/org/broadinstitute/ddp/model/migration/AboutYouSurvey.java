package org.broadinstitute.ddp.model.migration;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public final class AboutYouSurvey implements Gen2Survey {
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
}
