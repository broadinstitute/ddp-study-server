package org.broadinstitute.dsm.model.gbf;
import com.google.gson.JsonObject;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import javax.xml.bind.annotation.XmlElement;

public class Address {

    @XmlElement(name="Company")
    private String company;

    @XmlElement(name="AddressLine1")
    private String addressLine1;

    @XmlElement(name="AddressLine2")
    private String addressLine2;

    @XmlElement(name="City")
    private String city;

    @XmlElement(name="State")
    private String state;

    @XmlElement(name="ZipCode")
    private String zipCode;

    @XmlElement(name="Country")
    private String country;

    @XmlElement(name="PhoneNumber")
    private String phoneNumber;

    public Address() {
    }

    public Address(String addressLine1, String city, String state, String zipCode,
                   String country, String phoneNumber) {
        this(null, addressLine1, null, city, state, zipCode, country, phoneNumber);
    }

    public Address(String recipientName, String addressLine1, String addressLine2, String city, String state, String zipCode,
                   String country, String phoneNumber) {

        this.company = recipientName;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.state = state;
        this.zipCode = zipCode;
        this.country = country;
        this.phoneNumber = phoneNumber;

    }

    public boolean isComplete(){
        return  StringUtils.isNotBlank(this.company) && StringUtils.isNotBlank(this.addressLine1) &&
                StringUtils.isNotBlank(this.city) && StringUtils.isNotBlank(this.state) &&
                StringUtils.isNotBlank(this.zipCode) && StringUtils.isNotBlank(this.country) &&
                StringUtils.isNotBlank(this.phoneNumber);
    }

    public static Address fromElasticParticipantAddress(String fullName, JsonObject esAddress) {
        return new Address(fullName, esAddress.get("street1").getAsString(),
                esAddress.get("street2").getAsString(),
                esAddress.get("city").getAsString(),
                esAddress.get("state").getAsString(),
                esAddress.get("zip").getAsString(),
                esAddress.get("country").getAsString(),
                esAddress.get("phone").getAsString());
    }

    @Override
    public String toString() {
        return "Address{" +
                "company='" + company + '\'' +
                ", addressLine1='" + addressLine1 + '\'' +
                ", addressLine2='" + addressLine2 + '\'' +
                ", city='" + city + '\'' +
                ", state='" + state + '\'' +
                ", zipCode='" + zipCode + '\'' +
                ", country='" + country + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                '}';
    }
}
