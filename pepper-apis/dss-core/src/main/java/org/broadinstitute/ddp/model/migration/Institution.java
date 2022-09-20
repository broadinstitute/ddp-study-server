package org.broadinstitute.ddp.model.migration;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public final class Institution {
    @SerializedName("institution")
    private String institution;

    @SerializedName("city")
    private String city;

    @SerializedName("state")
    private String state;

    @SerializedName("country")
    private String country;

    @SerializedName("institutionid")
    private String institutionId;
}
