package org.broadinstitute.ddp.model.activity.definition.question;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.util.MiscUtil;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class MatrixOptionDef {

    @NotBlank
    @SerializedName("stableId")
    private String stableId;

    @Valid
    @NotNull
    @SerializedName("optionLabelTemplate")
    private Template optionLabelTemplate;

    @Valid
    @SerializedName("tooltipTemplate")
    private Template tooltipTemplate;

    @SerializedName("exclusive")
    private boolean isExclusive;

    @NotNull
    @SerializedName("groupId")
    private String groupStableId;

    private transient Long optionId;

    public MatrixOptionDef(Long optionId, String stableId, Template optionLabelTemplate, Template tooltipTemplate,
                           String groupStableId, boolean isExclusive) {
        this.optionId = optionId;
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.optionLabelTemplate = MiscUtil.checkNonNull(optionLabelTemplate, "optionLabelTemplate");
        this.groupStableId = MiscUtil.checkNotBlank(groupStableId, "groupId");
        this.tooltipTemplate = tooltipTemplate;
        this.isExclusive = isExclusive;
    }

    /**
     * Constructs a picklist option definition object without a detail field.
     */
    public MatrixOptionDef(String stableId, Template optionLabelTemplate, String groupStableId) {
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.optionLabelTemplate = MiscUtil.checkNonNull(optionLabelTemplate, "optionLabelTemplate");
        this.groupStableId = MiscUtil.checkNotBlank(groupStableId, "groupId");
    }

    public static MatrixOptionDef buildExclusive(String stableId, Template optionLabelTemplate, String groupStableId) {
        MatrixOptionDef matrixOptionDef = new MatrixOptionDef(stableId, optionLabelTemplate, groupStableId);
        matrixOptionDef.isExclusive = true;
        return matrixOptionDef;
    }


    public String getStableId() {
        return stableId;
    }

    public void setStableId(String stableId) {
        this.stableId = stableId;
    }

    public Template getOptionLabelTemplate() {
        return optionLabelTemplate;
    }

    public void setOptionLabelTemplate(Template optionLabelTemplate) {
        this.optionLabelTemplate = optionLabelTemplate;
    }

    public Template getTooltipTemplate() {
        return tooltipTemplate;
    }

    public void setTooltipTemplate(Template tooltipTemplate) {
        this.tooltipTemplate = tooltipTemplate;
    }

    public boolean isExclusive() {
        return isExclusive;
    }

    public void setExclusive(boolean exclusive) {
        isExclusive = exclusive;
    }

    public String getGroupStableId() {
        return groupStableId;
    }

    public void setGroupStableId(String groupStableId) {
        this.groupStableId = groupStableId;
    }

    public Long getOptionId() {
        return optionId;
    }

    public void setOptionId(Long optionId) {
        this.optionId = optionId;
    }
}
