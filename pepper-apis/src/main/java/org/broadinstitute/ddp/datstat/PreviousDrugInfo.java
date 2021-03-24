package org.broadinstitute.ddp.datstat;

import com.google.api.client.util.Data;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.handlers.util.ParticipantInstitutionList;

import java.lang.reflect.Type;
import java.util.ArrayList;

@lombok.Data
public class PreviousDrugInfo extends DrugInfo {

    private Integer drugEndMonth = null;
    private Integer drugEndYear = null;

    public PreviousDrugInfo() {
    }

    public PreviousDrugInfo(String drugName, Integer drugStartMonth, Integer drugStartYear, Boolean clinicalTrial, Integer drugEndMonth, Integer drugEndYear) {
        super(drugName, drugStartMonth, drugStartYear, clinicalTrial);
        this.drugEndMonth = drugEndMonth;
        this.drugEndYear = drugEndYear;
    }

    public static ArrayList<PreviousDrugInfo> previousDrugJsonToArrayList(String json) {
        Type listType = new TypeToken<ArrayList<PreviousDrugInfo>>() {}.getType();
        return new Gson().fromJson(json, listType);
    }

    public boolean isEmpty() {
        return (super.isEmpty()&&
                ((drugEndMonth == null)||(drugEndMonth == -1))&&
                ((drugEndYear == null)||(drugEndYear == -1)));
    }

    public void cleanDates() {
        super.cleanDates();
        if ((drugEndMonth != null)&&(drugEndMonth == -1)) {
            drugEndMonth = null;
        }
        if ((drugEndYear != null)&&(drugEndYear == -1)) {
            drugEndYear = null;
        }
    }
}