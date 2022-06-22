package org.broadinstitute.ddp.model.activity.definition;


import java.util.List;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ActivityCategoryDef {
    @SerializedName("categoryCode")
    String code;

    @SerializedName("categoryName")
    String name;

    @SerializedName("subForms")
    private List<FormGroupDef> subForms;

}
