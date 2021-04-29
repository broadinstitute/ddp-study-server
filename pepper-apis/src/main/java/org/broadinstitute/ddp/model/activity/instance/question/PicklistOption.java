package org.broadinstitute.ddp.model.activity.instance.question;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.apache.commons.collections4.CollectionUtils;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.content.Renderable;
import org.broadinstitute.ddp.util.MiscUtil;

public class PicklistOption implements Renderable {

    @NotBlank
    @SerializedName("stableId")
    private String stableId;

    @NotNull
    @SerializedName("optionLabel")
    private String optionLabel;

    @SerializedName("tooltip")
    private String tooltip;

    @SerializedName("detailLabel")
    private String detailLabel;

    @SerializedName("allowDetails")
    private boolean isDetailsAllowed;

    @SerializedName("exclusive")
    private boolean isExclusive;

    @SerializedName("groupId")
    private String groupStableId;

    @SerializedName("nestedOptionsLabel")
    private String nestedOptionsLabel;

    @SerializedName("nestedOptions")
    private List<@Valid @NotNull PicklistOption> nestedOptions = new ArrayList<>();

    private transient long optionLabelTemplateId;
    private transient Long tooltipTemplateId;
    private transient Long detailLabelTemplateId;
    private transient Long nestedOptionsLabelTemplateId;

    /**
     * Constructs a picklist option. The detail label is required if detail field is allowed.
     */
    public PicklistOption(String stableId, long optionLabelTemplateId, Long tooltipTemplateId,
                          Long detailLabelTemplateId, boolean isDetailsAllowed, boolean isExclusive) {
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.optionLabelTemplateId = optionLabelTemplateId;
        this.tooltipTemplateId = tooltipTemplateId;
        this.isDetailsAllowed = isDetailsAllowed;
        this.isExclusive = isExclusive;
        if (isDetailsAllowed) {
            if (detailLabelTemplateId == null) {
                throw new IllegalArgumentException("detail label must be provided when allowing attached details field");
            }
            this.detailLabelTemplateId = detailLabelTemplateId;
        }
    }

    public PicklistOption(String stableId, long optionLabelTemplateId, Long tooltipTemplateId,
                          Long detailLabelTemplateId, boolean isDetailsAllowed, boolean isExclusive,
                          Long nestedPicklistTemplateId, List<PicklistOption> nestedOptions) {
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.optionLabelTemplateId = optionLabelTemplateId;
        this.tooltipTemplateId = tooltipTemplateId;
        this.isDetailsAllowed = isDetailsAllowed;
        this.isExclusive = isExclusive;
        if (isDetailsAllowed) {
            if (detailLabelTemplateId == null) {
                throw new IllegalArgumentException("detail label must be provided when allowing attached details field");
            }
            this.detailLabelTemplateId = detailLabelTemplateId;
        }
        this.nestedOptionsLabelTemplateId  = nestedPicklistTemplateId;
        this.nestedOptions = nestedOptions;
    }

    public PicklistOption(String groupStableId, String stableId, long optionLabelTemplateId, Long tooltipTemplateId,
                          Long detailLabelTemplateId, boolean isDetailsAllowed, boolean isExclusive) {
        this(stableId, optionLabelTemplateId, tooltipTemplateId, detailLabelTemplateId, isDetailsAllowed, isExclusive);
        this.groupStableId = groupStableId;
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

    public String getDetailLabel() {
        return detailLabel;
    }

    public boolean isDetailsAllowed() {
        return isDetailsAllowed;
    }

    public boolean isExclusive() {
        return isExclusive;
    }

    public long getOptionLabelTemplateId() {
        return optionLabelTemplateId;
    }

    public Long getTooltipTemplateId() {
        return tooltipTemplateId;
    }

    public Long getDetailLabelTemplateId() {
        return detailLabelTemplateId;
    }

    public String getGroupStableId() {
        return groupStableId;
    }

    public void setGroupStableId(String groupStableId) {
        this.groupStableId = groupStableId;
    }

    public List<PicklistOption> getNestedOptions() {
        return nestedOptions;
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        registry.accept(optionLabelTemplateId);
        if (detailLabelTemplateId != null) {
            registry.accept(detailLabelTemplateId);
        }
        if (tooltipTemplateId != null) {
            registry.accept(tooltipTemplateId);
        }
        if (nestedOptionsLabelTemplateId != null) {
            registry.accept(nestedOptionsLabelTemplateId);
        }
        if (CollectionUtils.isNotEmpty(nestedOptions)) {
            for (PicklistOption suboption : nestedOptions) {
                suboption.registerTemplateIds(registry);
            }
        }
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        optionLabel = rendered.get(optionLabelTemplateId);
        if (optionLabel == null) {
            throw new NoSuchElementException("No rendered template found for option label with id " + optionLabelTemplateId);
        }

        if (detailLabelTemplateId != null) {
            detailLabel = rendered.get(detailLabelTemplateId);
            if (detailLabel == null) {
                throw new NoSuchElementException("No rendered template found for option detail label with id " + detailLabelTemplateId);
            }
        }

        if (tooltipTemplateId != null) {
            tooltip = HtmlConverter.getPlainText(rendered.get(tooltipTemplateId));
            if (tooltip == null) {
                throw new NoSuchElementException("No rendered template found for tooltip with id " + tooltipTemplateId);
            }
        }

        if (nestedOptionsLabelTemplateId != null) {
            nestedOptionsLabel = HtmlConverter.getPlainText(rendered.get(nestedOptionsLabelTemplateId));
            if (nestedOptionsLabel == null) {
                throw new NoSuchElementException("No rendered template found for nested options label with id "
                        + nestedOptionsLabelTemplateId);
            }
        }

        if (CollectionUtils.isNotEmpty(nestedOptions)) {
            for (PicklistOption suboption : nestedOptions) {
                suboption.applyRenderedTemplates(rendered, style);
            }
        }

        if (style == ContentStyle.BASIC) {
            optionLabel = HtmlConverter.getPlainText(optionLabel);
            detailLabel = HtmlConverter.getPlainText(detailLabel);
        }
    }
}
