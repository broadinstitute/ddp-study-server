package org.broadinstitute.dsm.model.elastic;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ESComputed {

    @SerializedName("meqScore")
    private long meqScore;

    @SerializedName("meqChronotype")
    private String meqChronotype;
}
