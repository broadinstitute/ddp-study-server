package org.broadinstitute.ddp.export.json.structured;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;

public class AddressRecord {

    @SerializedName("guid")
    private String guid;
    @SerializedName("mailToName")
    private String mailToName;
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
    @SerializedName("plusCode")
    private String plusCode;
    @SerializedName("valid")
    private boolean isValid;

    public AddressRecord(MailAddress address) {
        this(address.getGuid(),
                address.getName(),
                address.getStreet1(),
                address.getStreet2(),
                address.getCity(),
                address.getState(),
                address.getZip(),
                address.getCountry(),
                address.getPhone(),
                address.getPlusCode(),
                DsmAddressValidationStatus.addressValidStatuses().contains(address.getStatusType()));
    }

    public AddressRecord(String guid, String mailToName, String street1, String street2, String city, String state,
                         String zip, String country, String phone, String plusCode, boolean isValid) {
        this.guid = guid;
        this.mailToName = mailToName;
        this.street1 = street1;
        this.street2 = street2;
        this.city = city;
        this.state = state;
        this.zip = zip;
        this.country = country;
        this.phone = phone;
        this.plusCode = plusCode;
        this.isValid = isValid;
    }
}
