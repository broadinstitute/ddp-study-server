package org.broadinstitute.ddp.model.activity.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.json.activity.ActivityInstanceSummary;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.NestedActivityRenderHint;
import org.broadinstitute.ddp.util.MiscUtil;

public final class NestedActivityBlock extends FormBlock {

    @NotBlank
    @SerializedName("activityCode")
    private String activityCode;

    @NotNull
    @SerializedName("renderHint")
    private NestedActivityRenderHint renderHint;

    @SerializedName("allowMultiple")
    private boolean allowMultiple;

    @SerializedName("addButtonText")
    private String addButtonText;

    @NotNull
    @SerializedName("instances")
    private List<ActivityInstanceSummary> instanceSummaries = new ArrayList<>();

    private transient Long addButtonTemplateId;

    public NestedActivityBlock(String activityCode, NestedActivityRenderHint renderHint,
                               boolean allowMultiple, Long addButtonTemplateId) {
        super(BlockType.ACTIVITY);
        this.activityCode = MiscUtil.checkNotBlank(activityCode, "activityCode");
        this.renderHint = MiscUtil.checkNonNull(renderHint, "renderHint");
        this.allowMultiple = allowMultiple;
        this.addButtonTemplateId = addButtonTemplateId;
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

    public Long getAddButtonTemplateId() {
        return addButtonTemplateId;
    }

    public String getAddButtonText() {
        return addButtonText;
    }

    public List<ActivityInstanceSummary> getInstanceSummaries() {
        return instanceSummaries;
    }

    public void addInstanceSummaries(List<ActivityInstanceSummary> instanceSummaries) {
        if (instanceSummaries != null) {
            this.instanceSummaries.addAll(instanceSummaries);
        }
    }

    @Override
    public Stream<Question> streamQuestions() {
        // Questions within the nested activity itself are not considered.
        return Stream.empty();
    }

    @Override
    public boolean isComplete() {
        // Nested child activity is checked for completeness separately.
        return true;
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        if (addButtonTemplateId != null) {
            registry.accept(addButtonTemplateId);
        }
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        if (addButtonTemplateId != null) {
            addButtonText = rendered.get(addButtonTemplateId);
            if (addButtonText == null) {
                throw new NoSuchElementException("No rendered template found for addButtonText with id " + addButtonTemplateId);
            }
        }
        if (style == ContentStyle.BASIC) {
            addButtonText = HtmlConverter.getPlainText(addButtonText);
        }
    }
}
