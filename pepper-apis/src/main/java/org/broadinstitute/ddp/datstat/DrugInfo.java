package org.broadinstitute.ddp.datstat;

import com.google.api.client.util.Data;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Type;
import java.util.ArrayList;

@lombok.Data

public class DrugInfo {

    private String drugName = Data.nullOf(String.class);
    private Integer drugStartMonth;
    private Integer drugStartYear;
    private Boolean clinicalTrial = false;

    public DrugInfo() {
    }

    public DrugInfo(String drugName, Integer drugStartMonth, Integer drugStartYear, Boolean clinicalTrial) {
        this.drugName = drugName;
        this.drugStartMonth = drugStartMonth;
        this.drugStartYear = drugStartYear;
        this.clinicalTrial = clinicalTrial;
    }

    public boolean isEmpty() {
        return (StringUtils.isBlank(drugName) &&
                ((this.drugStartMonth == null)||(this.drugStartMonth == -1)) &&
                ((this.drugStartYear == null)||(this.drugStartYear == -1)) &&
                ((this.clinicalTrial == null)||(!this.clinicalTrial)));
    }

    public static ArrayList<DrugInfo> drugJsonToArrayList(String json) {
        Type listType = new TypeToken<ArrayList<DrugInfo>>() {}.getType();
        return new Gson().fromJson(json, listType);
    }

    public void cleanDates() {
        if ((drugStartMonth != null)&&(drugStartMonth == -1)) {
            drugStartMonth = null;
        }
        if ((drugStartYear != null)&&(drugStartYear == -1)) {
            drugStartYear = null;
        }
    }
}