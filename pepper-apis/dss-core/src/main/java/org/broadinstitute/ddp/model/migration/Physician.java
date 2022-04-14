package org.broadinstitute.ddp.model.migration;

import com.google.gson.annotations.SerializedName;

public class Physician {

    @SerializedName("name")
    private String name;
    @SerializedName("institution")
    private String institution;
    @SerializedName("streetaddress")
    private String streetaddress;
    @SerializedName("city")
    private String city;
    @SerializedName("state")
    private String state;
    @SerializedName("zipcode")
    private String zipcode;
    @SerializedName("phonenumber")
    private String phonenumber;
    @SerializedName("physicianid")
    private String physicianid;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInstitution() {
        return institution;
    }

    public void setInstitution(String institution) {
        this.institution = institution;
    }

    public String getStreetaddress() {
        return streetaddress;
    }

    public void setStreetaddress(String streetaddress) {
        this.streetaddress = streetaddress;
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

    public String getZipcode() {
        return zipcode;
    }

    public void setZipcode(String zipcode) {
        this.zipcode = zipcode;
    }

    public String getPhonenumber() {
        return phonenumber;
    }

    public void setPhonenumber(String phonenumber) {
        this.phonenumber = phonenumber;
    }

    public String getPhysicianid() {
        return physicianid;
    }

    public void setPhysicianid(String physicianid) {
        this.physicianid = physicianid;
    }

}
