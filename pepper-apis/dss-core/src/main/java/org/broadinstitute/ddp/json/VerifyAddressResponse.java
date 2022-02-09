package org.broadinstitute.ddp.json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.address.AddressWarning;
import org.broadinstitute.ddp.model.address.MailAddress;

public class VerifyAddressResponse {

    public static final String WARNINGS_ENTERED = "entered";
    public static final String WARNINGS_SUGGESTED = "suggested";

    @SerializedName("studyGuid")
    private String studyGuid;

    @SerializedName("name")
    private String name;

    @SerializedName("street1")
    private String street1;

    @SerializedName("street2")
    private String street2;

    @SerializedName("city")
    private String city;

    @SerializedName("state")
    private String state;

    @SerializedName("country")
    private String country;

    @SerializedName("zip")
    private String zip;

    @SerializedName("phone")
    private String phone;

    @SerializedName("warnings")
    private Map<String, List<AddressWarning>> warnings;

    public VerifyAddressResponse(String studyGuid, String name, String street1, String street2,
                                 String city, String state, String country, String zip, String phone,
                                 List<AddressWarning> warningsForEntered, List<AddressWarning> warningsForSuggested) {
        this.studyGuid = studyGuid;
        this.name = name;
        this.street1 = street1;
        this.street2 = street2;
        this.city = city;
        this.state = state;
        this.country = country;
        this.zip = zip;
        this.phone = phone;
        this.warnings = new HashMap<>();
        this.warnings.put(WARNINGS_ENTERED, new ArrayList<>(warningsForEntered));
        this.warnings.put(WARNINGS_SUGGESTED, new ArrayList<>(warningsForSuggested));
    }

    public VerifyAddressResponse(String studyGuid, MailAddress address,
                                 List<AddressWarning> warningsForEntered,
                                 List<AddressWarning> warningsForSuggested) {
        this(studyGuid,
                address.getName(),
                address.getStreet1(),
                address.getStreet2(),
                address.getCity(),
                address.getState(),
                address.getCountry(),
                address.getZip(),
                address.getPhone(),
                warningsForEntered,
                warningsForSuggested);
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

    public Map<String, List<AddressWarning>> getWarnings() {
        return warnings;
    }
}
