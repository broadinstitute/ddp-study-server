package org.broadinstitute.ddp.model.activity.definition;


import java.util.List;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ActivityCategoryDef {
    @SerializedName("categoryCode")
    String code;

    @SerializedName("categoryName")
    String name;

    @SerializedName("activities")
    private List<FormGroupDef> activities;

}
