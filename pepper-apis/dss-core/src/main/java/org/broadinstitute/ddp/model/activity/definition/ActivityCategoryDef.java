package org.broadinstitute.ddp.model.activity.definition;


import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActivityCategoryDef {
    @SerializedName("categoryCode")
    String code;
    @SerializedName("categoryName")
    String name;
}
