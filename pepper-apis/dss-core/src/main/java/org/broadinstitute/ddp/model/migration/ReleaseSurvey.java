package org.broadinstitute.ddp.model.migration;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public final class ReleaseSurvey implements Gen2Survey {
    @SerializedName("datstat_firstname")
    private String datstatFirstname;

    @SerializedName("datstat_lastname")
    private String datstatLastname;

    @SerializedName("phone_number")
    private String phoneNumber;

    @SerializedName("street1")
    private String street1;

    @SerializedName("street2")
    private String street2;

    @SerializedName("city")
    private String city;

    @SerializedName("state")
    private String state;

    @SerializedName("postal_code")
    private String postalCode;

    @SerializedName("country")
    private String country;

    @SerializedName("physician_list")
    private List<Physician> physicianList = null;

    @SerializedName("institution_list")
    private List<Institution> institutions = null;

    @SerializedName("initial_biopsy_institution")
    private String initialBiopsyInstitution;

    @SerializedName("initial_biopsy_city")
    private String initialBiopsyCity;

    @SerializedName("initial_biopsy_state")
    private String initialBiopsyState;

    @SerializedName("agreement.agree")
    private Integer agreementAgree;

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
