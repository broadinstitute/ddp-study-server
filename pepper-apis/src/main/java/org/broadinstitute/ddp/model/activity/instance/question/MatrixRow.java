package org.broadinstitute.ddp.model.activity.instance.question;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.content.Renderable;
import org.broadinstitute.ddp.util.MiscUtil;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

public class MatrixRow implements Renderable {

    @NotBlank
    @SerializedName("stableId")
    private String stableId;

    @NotNull
    @SerializedName("questionLabel")
    private String questionLabel;

    @SerializedName("tooltip")
    private String tooltip;

    private transient long questionLabelTemplateId;
    private transient Long tooltipTemplateId;

    public MatrixRow(String stableId, String questionLabel, String tooltip, long questionLabelTemplateId, Long tooltipTemplateId) {
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.questionLabel = questionLabel;
        this.tooltip = tooltip;
        this.questionLabelTemplateId = questionLabelTemplateId;
        this.tooltipTemplateId = tooltipTemplateId;
    }

    public MatrixRow(String stableId, long questionLabelTemplateId, Long tooltipTemplateId) {
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.questionLabelTemplateId = questionLabelTemplateId;
        this.tooltipTemplateId = tooltipTemplateId;
    }

    public String getStableId() {
        return stableId;
    }

    public void setStableId(String stableId) {
        this.stableId = stableId;
    }

    public String getQuestionLabel() {
        return questionLabel;
    }

    public void setQuestionLabel(String questionLabel) {
        this.questionLabel = questionLabel;
    }

    public String getTooltip() {
        return tooltip;
    }

    public void setTooltip(String tooltip) {
        this.tooltip = tooltip;
    }

    public long getQuestionLabelTemplateId() {
        return questionLabelTemplateId;
    }

    public void setQuestionLabelTemplateId(long questionLabelTemplateId) {
        this.questionLabelTemplateId = questionLabelTemplateId;
    }

    public Long getTooltipTemplateId() {
        return tooltipTemplateId;
    }

    public void setTooltipTemplateId(Long tooltipTemplateId) {
        this.tooltipTemplateId = tooltipTemplateId;
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        registry.accept(questionLabelTemplateId);
        
        if (tooltipTemplateId != null) {
            registry.accept(tooltipTemplateId);
        }
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        questionLabel = rendered.get(questionLabelTemplateId);
        if (questionLabel == null) {
            throw new NoSuchElementException("No rendered template found for option label with id " + questionLabelTemplateId);
        }

        if (tooltipTemplateId != null) {
            tooltip = HtmlConverter.getPlainText(rendered.get(tooltipTemplateId));
            if (tooltip == null) {
                throw new NoSuchElementException("No rendered template found for tooltip with id " + tooltipTemplateId);
            }
        }

        if (style == ContentStyle.BASIC) {
            questionLabel = HtmlConverter.getPlainText(questionLabel);
        }
    }
}
