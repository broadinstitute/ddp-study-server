package org.broadinstitute.ddp.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import com.google.gson.annotations.SerializedName;

public class TestAddress {
    private String guid;
    @NotNull
    @SerializedName("theName")
    @Valid
    private TestName name;
    @NotEmpty
    @Size(min = 2, max = 100)
    private String street1;
    @Size(max = 100)
    private String street2;
    @NotEmpty
    @Size(min = 2, max = 100)
    private String city;
    @Size(max = 100)
    private String state;
    @NotEmpty
    private String country;
    @NotEmpty
    private String zip;
    @Min(4)
    private int cars = 4;
    @Size(min = 2)
    @SerializedName("theListOfNumbers")
    private List<Integer> someNumbers = Arrays.asList(5, 6);
    @SerializedName("someArrayOfNames")
    @Valid
    private TestName[] arrayOfNames = {};
    @SerializedName("listOfNames")
    @Valid
    private List<TestName> names;
    private String phone;
    private boolean isVerified;
    @Valid
    private Map<String, TestName> firstNameToNameMap = new HashMap<>();
    @Valid
    @SerializedName("namesInASet")
    private Set<TestName> setOfNames = new LinkedHashSet<>();


    public TestAddress() {
        super();
    }

    /**
     * Instantiates a TestAddress object.
     */
    public TestAddress(String firstName, String lastName, String street1, String street2, String city,
                       String state, String country, String zip, String phone, boolean isVerified) {
        this();
        this.name = new TestName(firstName, lastName);
        this.street1 = street1;
        this.street2 = street2;
        this.city = city;
        this.state = state;
        this.zip = zip;
        this.country = country;
        this.phone = phone;
        this.isVerified = isVerified;

    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public TestName getName() {
        return name;
    }

    public void setName(TestName name) {
        this.name = name;
    }

    public String getStreet1() {
        return street1;
    }

    public void setStreet1(String street1) {
        this.street1 = street1;
    }

    public String getStreet2() {
        return street2;
    }

    public void setStreet2(String street2) {
        this.street2 = street2;
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

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getZip() {
        return zip;
    }

    public void setZip(String zip) {
        this.zip = zip;
    }

    public void setCars(int cars) {
        this.cars = cars;
    }

    public void setSomeNumbers(List<Integer> someNumbers) {
        this.someNumbers = someNumbers;
    }

    public void setNames(List<TestName> names) {
        this.names = names;
    }

    public boolean isVerified() {
        return isVerified;
    }

    public void setVerified(boolean verified) {
        isVerified = verified;
    }

    public void addNameMapping(TestName name) {
        firstNameToNameMap.put(name.getFirst(), name);
    }

    public void addToSetOfNames(TestName... names) {
        setOfNames.addAll(Arrays.asList(names));
    }

    public void setArrayOfNames(TestName... names) {
        arrayOfNames = names;
    }


}
