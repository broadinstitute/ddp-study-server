package org.broadinstitute.lddp.handlers.util;

import com.google.api.client.util.Data;

public class PhysicianInfo {

    private String name = Data.nullOf(String.class);
    private String institution = Data.nullOf(String.class);
    private String streetAddress = Data.nullOf(String.class);
    private String city = Data.nullOf(String.class);
    private String state = Data.nullOf(String.class);
    private String zipCode = Data.nullOf(String.class);
    private String phoneNumber = Data.nullOf(String.class);
    private String physicianId = Data.nullOf(String.class);
    private String country = Data.nullOf(String.class);

    public PhysicianInfo() {
    }

    public PhysicianInfo(String name, String phone, String zipCode, String state, String city, String streetAddress, String institution,
                         String id, String country) {
        this.name = name;
        this.phoneNumber = phone;
        this.zipCode = zipCode;
        this.state = state;
        this.city = city;
        this.streetAddress = streetAddress;
        this.institution = institution;
        this.physicianId = id;
        this.country = country;
    }

    public String getName() {
        return name;
    }

    public String getInstitution() {
        return institution;
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getZipCode() {
        return zipCode;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getPhysicianId() {
        return physicianId;
    }

    public void setPhysicianId(String physicianId) {
        this.physicianId = physicianId;
    }

    public String getCountry() {
        return country;
    }
}
