package org.broadinstitute.ddp.db.dto.dsm;

import org.broadinstitute.ddp.model.user.EnrollmentStatusType;

/**
 * POJO for study participant info. Created specifically to support DSM integration JSON API.
 */
public class DsmStudyParticipant {
    private String participantId;
    private String firstName;
    private String lastName;
    private String mailToName;
    private String country;
    private String shortId;
    private String legacyShortId;
    private String validAddress;
    private String city;
    private String postalCode;
    private String street1;
    private String street2;
    private String state;
    private String userGuid;
    private transient long userId;

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getUserGuid() {
        return userGuid;
    }

    public void setUserGuid(String userGuid) {
        this.userGuid = userGuid;
    }

    public EnrollmentStatusType getEnrollmentStatusType() {
        return enrollmentStatusType;
    }

    public void setEnrollmentStatusType(EnrollmentStatusType enrollmentStatusType) {
        this.enrollmentStatusType = enrollmentStatusType;
    }

    private EnrollmentStatusType enrollmentStatusType;

    public String getParticipantId() {
        return participantId;
    }

    public void setParticipantId(String participantId) {
        this.participantId = participantId;
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

    public String getMailToName() {
        return mailToName;
    }

    public void setMailToName(String mailToName) {
        this.mailToName = mailToName;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getShortId() {
        return shortId;
    }

    public void setShortId(String shortId) {
        this.shortId = shortId;
    }

    public String getLegacyShortId() {
        return legacyShortId;
    }

    public void setLegacyShortId(String legacyShortId) {
        this.legacyShortId = legacyShortId;
    }

    /**
     * Report the validation status of the mailing address.
     * “0” for an address that EasyPost says is invalid.
     * “1” for an address that EasyPost says is valid and contains suggested EasyPost corrections.
     * “2” for an address that EasyPost says is valid but does not contain EasyPost suggested corrections.
     */
    public String getValidAddress() {
        return validAddress;
    }

    public void setValidAddress(String validAddress) {
        this.validAddress = validAddress;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
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

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
