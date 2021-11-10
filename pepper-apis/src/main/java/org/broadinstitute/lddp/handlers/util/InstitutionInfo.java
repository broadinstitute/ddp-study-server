package org.broadinstitute.lddp.handlers.util;

import com.google.api.client.util.Data;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Type;
import java.util.ArrayList;

public class InstitutionInfo {

    private String institution = Data.nullOf(String.class);
    private String city = Data.nullOf(String.class);
    private String state = Data.nullOf(String.class);
    private String institutionId = Data.nullOf(String.class);
    private String country = Data.nullOf(String.class);

    public InstitutionInfo() {
    }

    public InstitutionInfo(String institution, String city, String state, String id, String country) {
        this.institution = institution;
        this.state = state;
        this.city = city;
        this.institutionId = id;
        this.country = country;
    }

    public String getInstitution() {
        return institution;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getInstitutionId() {
        return institutionId;
    }

    public String getCountry() {
        return country;
    }

    public void setInstitutionId(String institutionId)
    {
        this.institutionId = institutionId;
    }

    public boolean isEmpty() {
        return StringUtils.isBlank(institution) && StringUtils.isBlank(city) && StringUtils.isBlank(state)
                && StringUtils.isBlank(institutionId) && StringUtils.isBlank(country);
    }

    public static ArrayList<InstitutionInfo> jsonToArrayList(String json) {
        Type listType = new TypeToken<ArrayList<InstitutionInfo>>() {}.getType();
        return new Gson().fromJson(json, listType);
    }
}