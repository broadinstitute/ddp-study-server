package org.broadinstitute.ddp.model.address;

import java.beans.ConstructorProperties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.constraints.Size;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;

/**
 * Main model class for MailAddress. All validation annotations in accessors to allow adding more via inheritance.
 * Note only minimal validation here. Just about anything goes except exceeding size of database column
 */
public class MailAddress {
    //Marking transient to have excluded from Gson
    private transient Long id;
    private String guid;
    private String name;
    private String street1;
    private String street2;
    private String city;
    private String state;
    private String country;
    private String zip;
    private String phone;
    private int validationStatus;
    private String validationStatusName; //place holder for mailing_address_validation_status.name
    private String description;
    private String plusCode = null;
    private boolean isDefault = false;

    private transient DsmAddressValidationStatus statusType;

    public MailAddress() {
    }

    public MailAddress(String name, String street1, String street2, String city,
                       String state, String country, String zip, String phone, String plusCode, String description,
                       DsmAddressValidationStatus validationStatus, boolean isDefault) {
        this.name = StringUtils.trim(name);
        this.street1 = StringUtils.trim(street1);
        this.street2 = StringUtils.trim(street2);
        this.city = StringUtils.trim(city);
        this.state = StringUtils.trim(state);
        this.zip = StringUtils.trim(zip);
        this.country = StringUtils.trim(country);
        this.phone = StringUtils.trim(phone);
        if (validationStatus != null) {
            this.validationStatus = validationStatus.getCode();
            this.statusType = validationStatus;
        }
        this.plusCode = plusCode;
        this.description = StringUtils.trim(description);
        this.isDefault = isDefault;
    }

    @ConstructorProperties({"id", "guid",
            "name", "street1", "street2", "city", "state", "zip", "country",
            "phone", "pluscode", "description", "status", "is_default"})
    public MailAddress(long id, String guid,
                       String name, String street1, String street2,
                       String city, String state, String zip, String country,
                       String phone, String plusCode, String description,
                       DsmAddressValidationStatus status, boolean isDefault) {
        this(name, street1, street2, city, state, country, zip, phone, plusCode, description, status, isDefault);
        this.id = id;
        this.guid = guid;
    }

    public String toAddressString() {
        return Stream.of(this.getStreet1(), this.getCity(),
                this.getState(), this.getZip(), this.getCountry())
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.joining(","));
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    @Size(max = 100)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Size(max = 100)
    public String getStreet1() {
        return street1;
    }

    public void setStreet1(String street1) {
        this.street1 = street1;
    }

    @Size(max = 100)
    public String getStreet2() {
        return street2;
    }

    public void setStreet2(String street2) {
        this.street2 = street2;
    }

    public String getCombinedStreet() {
        String street = street1;
        if (StringUtils.isNotBlank(street2)) {
            street += ", " + street2;
        }
        return street;
    }

    @Size(max = 100)
    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    @Size(max = 100)
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @Size(max = 100)
    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    @Size(max = 100)
    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    @Size(max = 100)
    public String getZip() {
        return zip;
    }

    public void setZip(String zip) {
        this.zip = zip;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPlusCode() {
        return plusCode;
    }

    public void setPlusCode(String plusCode) {
        this.plusCode = plusCode;
    }

    public DsmAddressValidationStatus getStatusType() {
        return statusType;
    }

    public int getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(int validationStatus) {
        this.validationStatus = validationStatus;
    }

    public void setValidationStatus(DsmAddressValidationStatus validationStatus) {
        this.validationStatus = validationStatus.getCode();
    }

    public String getValidationStatusName() {
        return validationStatusName;
    }

    public void setValidationStatusName(String validationStatusName) {
        this.validationStatusName = validationStatusName;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefaultAddress) {
        isDefault = isDefaultAddress;
    }

    /**
     * Change the property name to make it easier to work with SQL. Does not like the word "DEFAULT" in statements.
     *
     * @param isDefaultAddress boolean
     */
    public void setDefaultValue(boolean isDefaultAddress) {
        isDefault = isDefaultAddress;
    }

    public boolean getDefaultValue() {
        return isDefault;
    }
}
