package org.broadinstitute.ddp.model.migration;

import com.google.gson.annotations.SerializedName;

public class Institution {

    @SerializedName("institution")
    private String institution;
    @SerializedName("city")
    private String city;
    @SerializedName("state")
    private String state;
    @SerializedName("institutionid")
    private String institutionId;

    public String getInstitution() {
        return institution;
    }

    public void setInstitution(String institution) {
        this.institution = institution;
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

    public String getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(String institutionId) {
        this.institutionId = institutionId;
    }

}
