package org.broadinstitute.ddp.model.migration;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public final class LovedOneSurvey implements Gen2Survey {
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
}
