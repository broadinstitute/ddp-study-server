package org.broadinstitute.ddp.db.dto;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class NotificationTemplateSubstitutionDto {
    @SerializedName("variableName")
    @ColumnName("substitution_variable_name")
    String variableName;

    @SerializedName("value")
    @ColumnName("substitution_value")
    String value;
}
