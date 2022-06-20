package org.broadinstitute.ddp.model.activity.definition;


import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FormGroupDef {
    @SerializedName("formCode")
    String code;
    @SerializedName("formName")
    String name;
}
