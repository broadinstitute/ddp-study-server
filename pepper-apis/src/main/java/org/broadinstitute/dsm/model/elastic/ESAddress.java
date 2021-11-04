package org.broadinstitute.dsm.model.elastic;

import com.google.gson.annotations.SerializedName;
import lombok.Setter;

@Setter
public class ESAddress {

    @SerializedName("street1")
    private String street1;

    @SerializedName("street2")
    private String street2;

    @SerializedName("city")
    private String city;

    @SerializedName("state")
    private String state;

    @SerializedName("zip")
    private String zip;

    @SerializedName("country")
    private String country;

    @SerializedName("phone")
    private String phone;

    @SerializedName("mailToName")
    private String recipient;

    @SerializedName("valid")
    private boolean valid;

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

    public String getZip() {
        return zip;
    }

    public String getCountry() {
        return country;
    }

    public String getPhone() {
        return phone;
    }

    public String getRecipient() {
        return recipient;
    }

    public boolean isValid() { return valid; }
}
