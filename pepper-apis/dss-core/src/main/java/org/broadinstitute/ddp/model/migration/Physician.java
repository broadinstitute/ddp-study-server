package org.broadinstitute.ddp.model.migration;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public final class Physician {
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

    @SerializedName("country")
    private String country;

    @SerializedName("zipcode")
    private String zipcode;

    @SerializedName("phonenumber")
    private String phonenumber;

    @SerializedName("physicianid")
    private String physicianid;
}
