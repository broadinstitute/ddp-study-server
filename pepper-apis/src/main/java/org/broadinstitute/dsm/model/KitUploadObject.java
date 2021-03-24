package org.broadinstitute.dsm.model;

public class KitUploadObject extends KitRequest {

    private String firstName;
    private String lastName;
    private String street1;
    private String street2;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private String easyPostAddressId;
    private String phoneNumber;
    private String shippingCarrier;


    public KitUploadObject(String externalOrderNumber, String ddpParticipantId, String shortId, String firstName, String lastName,
                           String street1, String street2, String city, String state, String postalCode, String country, String phoneNumber) {
        super(null, ddpParticipantId, shortId, null, externalOrderNumber, null, null, null, null);
        this.firstName = firstName;
        this.lastName = lastName;
        this.street1 = street1;
        this.street2 = street2;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.country = country;
        this.phoneNumber = phoneNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getStreet1() {
        return street1;
    }

    public String getStreet2() {
        return street2;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCountry() {
        return country;
    }

    public String getEasyPostAddressId() {
        return easyPostAddressId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setEasyPostAddressId(String easyPostAddressId) {
        this.easyPostAddressId = easyPostAddressId;
    }

    @Override
    public String toString() {
        return "KitUploadObject{" +
                "ddpParticipantId='" + getParticipantId() + '\'' +
                ", externalOrderNumber='" + getExternalOrderNumber() + '\'' +
                ", shortId='" + getShortId() + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", street1='" + street1 + '\'' +
                ", street2='" + street2 + '\'' +
                ", city='" + city + '\'' +
                ", state='" + state + '\'' +
                ", postalCode='" + postalCode + '\'' +
                ", country='" + country + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                '}';
    }
}
