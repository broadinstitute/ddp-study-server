package org.broadinstitute.ddp.model.migration;

import com.google.gson.annotations.SerializedName;

public class OtherCancer {

    @SerializedName("diseasename")
    private String diseasename;
    @SerializedName("diagnosisyear")
    private Integer diagnosisyear;

    public String getDiseasename() {
        return diseasename;
    }

    public void setDiseasename(String diseasename) {
        this.diseasename = diseasename;
    }

    public Integer getDiagnosisyear() {
        return diagnosisyear;
    }

    public void setDiagnosisyear(Integer diagnosisyear) {
        this.diagnosisyear = diagnosisyear;
    }

}
