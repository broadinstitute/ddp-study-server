package org.broadinstitute.ddp.datstat;

import com.google.api.client.util.Data;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by ebaker on 9/2/16.
 */
public class DiseaseInfo
{
    public DiseaseInfo(String diseaseName, Integer diagnosisYear) {
        this.diseaseName = diseaseName;
        this.diagnosisYear = diagnosisYear;
    }
    private String diseaseName = Data.nullOf(String.class);
    private Integer diagnosisYear = Data.nullOf(Integer.class);

    public String getDiseaseName() {
        return diseaseName;
    }

    public Integer getDiagnosisYear() {
        return diagnosisYear;
    }

    public boolean isEmpty(){
        return (diagnosisYear == null && StringUtils.isBlank(diseaseName));

    }
}