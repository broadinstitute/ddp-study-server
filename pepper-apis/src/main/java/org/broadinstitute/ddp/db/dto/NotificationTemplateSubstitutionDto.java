package org.broadinstitute.ddp.db.dto;

import com.google.gson.annotations.SerializedName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class NotificationTemplateSubstitutionDto {

    @SerializedName("variableName")
    private final String variableName;

    @SerializedName("value")
    private final String value;

    @JdbiConstructor
    public NotificationTemplateSubstitutionDto(String variableName, String value) {
        this.variableName = variableName;
        this.value = value;
    }

    public String getVariableName() {
        return variableName;
    }

    public String getValue() {
        return value;
    }
}
