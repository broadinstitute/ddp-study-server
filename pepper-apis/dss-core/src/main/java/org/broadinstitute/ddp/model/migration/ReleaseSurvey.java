package org.broadinstitute.ddp.model.migration;

import java.util.List;

import com.google.gson.annotations.SerializedName;

public class ReleaseSurvey implements Gen2Survey {

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

    public String getDatstatFirstname() {
        return datstatFirstname;
    }

    public void setDatstatFirstname(String datstatFirstname) {
        this.datstatFirstname = datstatFirstname;
    }

    public String getDatstatLastname() {
        return datstatLastname;
    }

    public void setDatstatLastname(String datstatLastname) {
        this.datstatLastname = datstatLastname;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getStreet1() {
        return street1;
    }

    public void setStreet1(String street1) {
        this.street1 = street1;
    }

    public String getStreet2() {
        return street2;
    }

    public void setStreet2(String street2) {
        this.street2 = street2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public List<Physician> getPhysicianList() {
        return physicianList;
    }

    public void setPhysicianList(List<Physician> physicianList) {
        this.physicianList = physicianList;
    }

    public List<Institution> getInstitutions() {
        return institutions;
    }

    public void setInstitutions(List<Institution> institutions) {
        this.institutions = institutions;
    }

    public String getInitialBiopsyInstitution() {
        return initialBiopsyInstitution;
    }

    public void setInitialBiopsyInstitution(String initialBiopsyInstitution) {
        this.initialBiopsyInstitution = initialBiopsyInstitution;
    }

    public String getInitialBiopsyCity() {
        return initialBiopsyCity;
    }

    public void setInitialBiopsyCity(String initialBiopsyCity) {
        this.initialBiopsyCity = initialBiopsyCity;
    }

    public String getInitialBiopsyState() {
        return initialBiopsyState;
    }

    public void setInitialBiopsyState(String initialBiopsyState) {
        this.initialBiopsyState = initialBiopsyState;
    }

    public Integer getAgreementAgree() {
        return agreementAgree;
    }

    public void setAgreementAgree(Integer agreementAgree) {
        this.agreementAgree = agreementAgree;
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
