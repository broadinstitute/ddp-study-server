package org.broadinstitute.ddp.db.dto;

import java.io.Serializable;
import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
@NoArgsConstructor
@Data
public class ActivityFormGroupDto implements Serializable {
    @ColumnName("category_code")
    private String categoryCode;

    @ColumnName("category_name")
    private String categoryName;

    @ColumnName("form_code")
    private String formCode;

    @ColumnName("form_name")
    private String formName;

    @ColumnName("parent_form_code")
    private String parentFormCode;


    public static class Deserializer implements JsonDeserializer<ActivityFormGroupDto> {
        @Override
        public ActivityFormGroupDto deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            String formCode = json.getAsString();
            if (formCode == null) {
                return null;
            }
            ActivityFormGroupDto formGroupDto = new ActivityFormGroupDto();
            formGroupDto.setFormCode(formCode);
            return formGroupDto;
        }
    }
}
