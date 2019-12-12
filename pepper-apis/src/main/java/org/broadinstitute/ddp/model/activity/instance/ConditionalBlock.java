package org.broadinstitute.ddp.model.activity.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.BlockType;

public final class ConditionalBlock extends FormBlock implements Numberable {

    @NotNull
    @SerializedName("control")
    private Question control;

    @NotNull
    @SerializedName("nested")
    private List<FormBlock> nested = new ArrayList<>();

    @SerializedName(DISPLAY_NUMBER)
    private Integer displayNumber;

    private transient boolean hideDisplayNumber;

    public ConditionalBlock(Question control) {
        super(BlockType.CONDITIONAL);
        this.control = control;
        this.hideDisplayNumber = control.shouldHideQuestionNumber();
    }

    public Question getControl() {
        return control;
    }

    public List<FormBlock> getNested() {
        return nested;
    }

    @Override
    public boolean isComplete() {
        if (!shown) {
            return true;
        }
        boolean complete = control.passesDeferredValidations();
        if (complete) {
            for (FormBlock child : nested) {
                complete = child.isComplete();
                if (!complete) {
                    break;
                }
            }
        }
        return complete;
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        control.registerTemplateIds(registry);
        for (FormBlock child : nested) {
            child.registerTemplateIds(registry);
        }
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        control.applyRenderedTemplates(rendered, style);
        for (FormBlock child : nested) {
            child.applyRenderedTemplates(rendered, style);
        }
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
}
