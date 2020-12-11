package org.broadinstitute.ddp.db.dto;

import java.util.Objects;

import com.google.gson.annotations.SerializedName;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class NotificationTemplateSubstitutionDto {

    @SerializedName("variableName")
    private final String variableName;

    @SerializedName("value")
    private final String value;

    @JdbiConstructor
    public NotificationTemplateSubstitutionDto(
            @ColumnName("substitution_variable_name") String variableName,
            @ColumnName("substitution_value") String value) {
        this.variableName = variableName;
        this.value = value;
    }

    public String getVariableName() {
        return variableName;
    }

    public String getValue() {
        return value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(variableName, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NotificationTemplateSubstitutionDto that = (NotificationTemplateSubstitutionDto) o;
        return variableName.equals(that.variableName)
                && value.equals(that.value);
    }
}
