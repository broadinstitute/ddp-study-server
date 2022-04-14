package org.broadinstitute.ddp.model.migration;

public class SurveyAddress {


    private String fullName;
    private String street1;
    private String street2;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private String phone;


    public SurveyAddress(String fullName, String street1, String street2,
                         String city, String state, String country, String postalCode, String phone) {
        this.fullName = fullName;
        this.street1 = street1;
        this.street2 = street2;
        this.city = city;
        this.state = state;
        this.country = country;
        this.postalCode = postalCode;
        this.phone = phone;
    }

    public String getFullName() {
        return fullName;
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

    public String getCountry() {
        return country;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getPhone() {
        return phone;
    }
}
