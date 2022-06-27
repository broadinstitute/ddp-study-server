package org.broadinstitute.ddp.model.activity.definition;


import java.util.List;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FormGroupDef {
    @SerializedName("formCode")
    String code;
    @SerializedName("formName")
    String name;

    @SerializedName("subForms")
    private List<FormGroupDef> subForms;

}
