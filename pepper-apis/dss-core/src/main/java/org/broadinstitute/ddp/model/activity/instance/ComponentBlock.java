package org.broadinstitute.ddp.model.activity.instance;

import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.util.MiscUtil;

/**
 * Base type for all embedded component blocks
 */
public final class ComponentBlock extends FormBlock implements Numberable  {

    @NotNull
    @SerializedName("component")
    private FormComponent formComponent;

    @SerializedName(DISPLAY_NUMBER)
    private Integer displayNumber;

    private transient boolean hideDisplayNumber;

    public ComponentBlock(FormComponent formComponent) {
        super(BlockType.COMPONENT);
        MiscUtil.checkNonNull(formComponent, "formComponent");
        this.formComponent = formComponent;
        this.hideDisplayNumber = formComponent.hideDisplayNumber;
    }

    @Override
    public Stream<Question> streamQuestions() {
        return Stream.empty();
    }

    @Override
    public boolean isComplete() {
        // todo: do checking as necessary
        return true;
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        formComponent.registerTemplateIds(registry);
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        formComponent.applyRenderedTemplates(rendered, style);
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

    public FormComponent getFormComponent() {
        return formComponent;
    }
}
