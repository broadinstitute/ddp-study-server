package org.broadinstitute.ddp.model.migration;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public final class OtherCancer {
    @SerializedName("diseasename")
    private String diseasename;

    @SerializedName("diagnosisyear")
    private Integer diagnosisyear;
}
