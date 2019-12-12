package org.broadinstitute.ddp.model.activity.instance;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.ComponentType;

public abstract class FormComponent implements Numberable {

    @NotNull
    @SerializedName("componentType")
    protected ComponentType componentType;

    @SerializedName(DISPLAY_NUMBER)
    private Integer displayNumber;

    protected transient boolean hideDisplayNumber;

    @NotNull
    @SerializedName("parameters")
    // derived classes should fill this map explicitly.  this is where the client
    // gets the list of parameters
    protected Map<String, Object> parameters = new LinkedHashMap<>();

    public FormComponent(ComponentType componentType) {
        this.componentType = componentType;
    }

    @Override
    public void setDisplayNumber(Integer displayNumber) {
        this.displayNumber = displayNumber;
    }

    @Override
    public Integer getDisplayNumber() {
        return displayNumber;
    }

    public boolean shouldHideNumber() {
        return hideDisplayNumber;
    }

    public ComponentType getComponentType() {
        return componentType;
    }
}

