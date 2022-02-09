package org.broadinstitute.ddp.model.activity.instance;

import java.util.function.Consumer;

import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;

import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.Renderable;
import org.broadinstitute.ddp.model.activity.types.ComponentType;

public abstract class FormComponent implements Numberable, Renderable {

    @NotNull
    @SerializedName("componentType")
    protected ComponentType componentType;

    @SerializedName(DISPLAY_NUMBER)
    private Integer displayNumber;

    protected transient boolean hideDisplayNumber;

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

    @Override
    public boolean shouldHideNumber() {
        return hideDisplayNumber;
    }

    @Override
    public abstract void registerTemplateIds(Consumer<Long> registry);

    @Override
    public abstract void applyRenderedTemplates(Provider<String> rendered, ContentStyle style);

    public ComponentType getComponentType() {
        return componentType;
    }
}

