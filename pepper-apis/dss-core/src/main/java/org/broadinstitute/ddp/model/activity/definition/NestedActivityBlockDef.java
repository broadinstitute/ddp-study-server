package org.broadinstitute.ddp.model.activity.definition;

import java.util.stream.Stream;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.NestedActivityRenderHint;

public final class NestedActivityBlockDef extends FormBlockDef {

    @NotBlank
    @SerializedName("activityCode")
    private String activityCode;

    @NotNull
    @SerializedName("renderHint")
    private NestedActivityRenderHint renderHint;

    @SerializedName("allowMultiple")
    private boolean allowMultiple;

    @Valid
    @SerializedName("addButtonTemplate")
    private Template addButtonTemplate;

    public NestedActivityBlockDef(String activityCode, NestedActivityRenderHint renderHint,
                                  boolean allowMultiple, Template addButtonTemplate) {
        super(BlockType.ACTIVITY);
        this.activityCode = activityCode;
        this.renderHint = renderHint;
        this.allowMultiple = allowMultiple;
        this.addButtonTemplate = addButtonTemplate;
    }

    public String getActivityCode() {
        return activityCode;
    }

    public NestedActivityRenderHint getRenderHint() {
        return renderHint;
    }

    public boolean isAllowMultiple() {
        return allowMultiple;
    }

    public Template getAddButtonTemplate() {
        return addButtonTemplate;
    }

    @Override
    public Stream<QuestionDef> getQuestions() {
        // Questions within the nested activity itself are not considered.
        return Stream.of();
    }
}
