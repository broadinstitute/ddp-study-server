package org.broadinstitute.ddp.model.activity.definition;


import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.broadinstitute.ddp.db.dto.ActivityFormGroupDto;

@Getter
@Setter
@Builder
public class FormGroupDef {
    @SerializedName("formCode")
    String code;
    @SerializedName("formName")
    String name;

    public static FormGroupDef from(ActivityFormGroupDto formGroupDto) {
        if (formGroupDto == null) {
            return null;
        }
        return builder().code(formGroupDto.getFormCode())
                .name(formGroupDto.getFormName()).build();
    }
}
