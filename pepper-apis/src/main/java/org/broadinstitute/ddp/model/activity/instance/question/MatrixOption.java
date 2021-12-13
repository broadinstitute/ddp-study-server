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

public class MatrixOption implements Renderable {

    @NotBlank
    @SerializedName("stableId")
    private final String stableId;

    @NotNull
    @SerializedName("optionLabel")
    private String optionLabel;

    @SerializedName("tooltip")
    private String tooltip;

    @SerializedName("exclusive")
    private final boolean isExclusive;

    @NotNull
    @SerializedName("groupId")
    private String groupStableId;

    private final transient long optionLabelTemplateId;
    private final transient Long tooltipTemplateId;

    public MatrixOption(String stableId, long optionLabelTemplateId, Long tooltipTemplateId, boolean isExclusive) {
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.optionLabelTemplateId = optionLabelTemplateId;
        this.tooltipTemplateId = tooltipTemplateId;
        this.isExclusive = isExclusive;
    }

    public MatrixOption(String stableId, long optionLabelTemplateId, Long tooltipTemplateId, String groupStableId, boolean isExclusive) {
        this(stableId, optionLabelTemplateId, tooltipTemplateId, isExclusive);
        this.groupStableId = groupStableId;
    }

    public MatrixOption(String stableId, long optionLabelTemplateId, String groupStableId) {
        this(stableId, optionLabelTemplateId, null, groupStableId, false);
    }

    public String getStableId() {
        return stableId;
    }

    public String getOptionLabel() {
        return optionLabel;
    }

    public String getTooltip() {
        return tooltip;
    }

    public boolean isExclusive() {
        return isExclusive;
    }

    public String getGroupStableId() {
        return groupStableId;
    }

    public Long getTooltipTemplateId() {
        return tooltipTemplateId;
    }

    public long getOptionLabelTemplateId() {
        return optionLabelTemplateId;
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        registry.accept(optionLabelTemplateId);

        if (tooltipTemplateId != null) {
            registry.accept(tooltipTemplateId);
        }
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        optionLabel = rendered.get(optionLabelTemplateId);
        if (optionLabel == null) {
            throw new NoSuchElementException("No rendered template found for option label with id " + optionLabelTemplateId);
        }

        if (tooltipTemplateId != null) {
            tooltip = HtmlConverter.getPlainText(rendered.get(tooltipTemplateId));
            if (tooltip == null) {
                throw new NoSuchElementException("No rendered template found for tooltip with id " + tooltipTemplateId);
            }
        }

        if (style == ContentStyle.BASIC) {
            optionLabel = HtmlConverter.getPlainText(optionLabel);
        }
    }
}
