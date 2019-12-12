package org.broadinstitute.ddp.model.activity.instance.question;

import java.util.NoSuchElementException;
import java.util.function.Consumer;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
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

    @SerializedName("detailLabel")
    private String detailLabel;

    @SerializedName("allowDetails")
    private boolean isDetailsAllowed;

    @SerializedName("exclusive")
    private boolean isExclusive;

    @SerializedName("groupId")
    private String groupStableId;

    private transient long optionLabelTemplateId;
    private transient Long detailLabelTemplateId;

    /**
     * Constructs a picklist option. The detail label is required if detail field is allowed.
     */
    public PicklistOption(String stableId, long optionLabelTemplateId, Long detailLabelTemplateId,
                          boolean isDetailsAllowed, boolean isExclusive) {
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.optionLabelTemplateId = optionLabelTemplateId;
        this.isDetailsAllowed = isDetailsAllowed;
        this.isExclusive = isExclusive;
        if (isDetailsAllowed) {
            if (detailLabelTemplateId == null) {
                throw new IllegalArgumentException("detail label must be provided when allowing attached details field");
            }
            this.detailLabelTemplateId = detailLabelTemplateId;
        }
    }

    public PicklistOption(String groupStableId, String stableId, long optionLabelTemplateId, Long detailLabelTemplateId,
                          boolean isDetailsAllowed, boolean isExclusive) {
        this(stableId, optionLabelTemplateId, detailLabelTemplateId, isDetailsAllowed, isExclusive);
        this.groupStableId = groupStableId;
    }

    public String getStableId() {
        return stableId;
    }

    public String getOptionLabel() {
        return optionLabel;
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

    public Long getDetailLabelTemplateId() {
        return detailLabelTemplateId;
    }

    public String getGroupStableId() {
        return groupStableId;
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        registry.accept(optionLabelTemplateId);
        if (detailLabelTemplateId != null) {
            registry.accept(detailLabelTemplateId);
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

        if (style == ContentStyle.BASIC) {
            optionLabel = HtmlConverter.getPlainText(optionLabel);
            detailLabel = HtmlConverter.getPlainText(detailLabel);
        }
    }
}
