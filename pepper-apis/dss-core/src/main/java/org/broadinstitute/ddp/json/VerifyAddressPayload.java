package org.broadinstitute.ddp.json;

import javax.validation.constraints.Size;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.address.MailAddress;

public class VerifyAddressPayload {

    @SerializedName("studyGuid")
    private String studyGuid;

    @Size(max = 100)
    @SerializedName("name")
    private String name;

    @Size(max = 100)
    @SerializedName("street1")
    private String street1;

    @Size(max = 100)
    @SerializedName("street2")
    private String street2;

    @Size(max = 100)
    @SerializedName("city")
    private String city;

    @Size(max = 100)
    @SerializedName("state")
    private String state;

    @Size(max = 100)
    @SerializedName("country")
    private String country;

    @Size(max = 100)
    @SerializedName("zip")
    private String zip;

    @Size(max = 100)
    @SerializedName("phone")
    private String phone;

    public VerifyAddressPayload(String studyGuid, String name, String street1, String street2,
                                String city, String state, String country, String zip, String phone) {
        this.studyGuid = studyGuid;
        this.name = name;
        this.street1 = street1;
        this.street2 = street2;
        this.city = city;
        this.state = state;
        this.country = country;
        this.zip = zip;
        this.phone = phone;
    }

    public VerifyAddressPayload(String studyGuid, MailAddress address) {
        this(studyGuid,
                address.getName(),
                address.getStreet1(),
                address.getStreet2(),
                address.getCity(),
                address.getState(),
                address.getCountry(),
                address.getZip(),
                address.getPhone());
    }

    public MailAddress toMailAddress() {
        return new MailAddress(
                name, street1, street2,
                city, state, country,
                zip, phone, null, null, null, false);
    }

    public String getStudyGuid() {
        return studyGuid;
    }

    public String getName() {
        return name;
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

    public String getZip() {
        return zip;
    }

    public String getPhone() {
        return phone;
    }
}
