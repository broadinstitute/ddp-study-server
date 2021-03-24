package org.broadinstitute.ddp.datstat;

import com.google.api.client.util.Data;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Type;
import java.util.ArrayList;

public class PhysicianInfo {

    private String name = Data.nullOf(String.class);
    private String institution = Data.nullOf(String.class);
    private String streetAddress = Data.nullOf(String.class);
    private String city = Data.nullOf(String.class);
    private String state = Data.nullOf(String.class);
    private String zipCode = Data.nullOf(String.class);
    private String phoneNumber = Data.nullOf(String.class);
    private String physicianId = Data.nullOf(String.class);
    private String country = Data.nullOf(String.class);

    public PhysicianInfo() {
    }

    public PhysicianInfo(String name, String phone, String zipCode, String state, String city, String streetAddress, String institution, String id, String country) {
        this.name = name;
        this.phoneNumber = phone;
        this.zipCode = zipCode;
        this.state = state;
        this.city = city;
        this.streetAddress = streetAddress;
        this.institution = institution;
        this.physicianId = id;
        this.country = country;
    }

    public String getName() {
        return name;
    }

    public String getInstitution() {
        return institution;
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getZipCode() {
        return zipCode;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getPhysicianId() {
        return physicianId;
    }

    public String getCountry() {
        return country;
    }

    public void setPhysicianId(String physicianId)
    {
        this.physicianId = physicianId;
    }

    public boolean isEmpty() {
        return
                (StringUtils.isBlank(name) &&
                        StringUtils.isBlank(institution) &&
                        StringUtils.isBlank(streetAddress) &&
                        StringUtils.isBlank(city) &&
                        StringUtils.isBlank(state) &&
                        StringUtils.isBlank(zipCode) &&
                        StringUtils.isBlank(phoneNumber) && StringUtils.isBlank(physicianId) && StringUtils.isBlank(country));
    }

    public static ArrayList<PhysicianInfo> jsonToArrayList(String json) {
        Type listType = new TypeToken<ArrayList<PhysicianInfo>>() {}.getType();
        return new Gson().fromJson(json, listType);
    }
}