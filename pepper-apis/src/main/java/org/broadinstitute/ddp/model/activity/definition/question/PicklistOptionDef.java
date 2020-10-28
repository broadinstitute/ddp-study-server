package org.broadinstitute.ddp.model.activity.definition.question;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.util.MiscUtil;

public class PicklistOptionDef {

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

    @Valid
    @SerializedName("detailLabelTemplate")
    private Template detailLabelTemplate;

    @SerializedName("allowDetails")
    private boolean isDetailsAllowed;

    @SerializedName("exclusive")
    private boolean isExclusive;

    @SerializedName("nestedOptionsLabelTemplate")
    private Template nestedOptionsLabelTemplate;

    @SerializedName("nestedPicklistOptions")
    private List<@Valid @NotNull PicklistOptionDef> nestedPicklistOptions = new ArrayList<>();

    private transient Long optionId;

    public static PicklistOptionDef newExclusive(String stableId, Template optionLabelTemplate) {
        PicklistOptionDef optionDef = new PicklistOptionDef(stableId, optionLabelTemplate);
        optionDef.isExclusive = true;
        return optionDef;
    }

    public static PicklistOptionDef newExclusive(String stableId, Template optionLabelTemplate, Template detailLabelTemplate) {
        PicklistOptionDef optionDef = new PicklistOptionDef(stableId, optionLabelTemplate, detailLabelTemplate);
        optionDef.isExclusive = true;
        return optionDef;
    }

    /**
     * Constructs a picklist option definition object without a detail field.
     */
    public PicklistOptionDef(String stableId, Template optionLabelTemplate) {
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.optionLabelTemplate = MiscUtil.checkNonNull(optionLabelTemplate, "optionLabelTemplate");
        this.isDetailsAllowed = false;
    }

    /**
     * Constructs a picklist option definition object with a detail field. The detail label is required.
     */
    public PicklistOptionDef(String stableId, Template optionLabelTemplate, Template detailLabelTemplate) {
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.optionLabelTemplate = MiscUtil.checkNonNull(optionLabelTemplate, "optionLabelTemplate");
        this.isDetailsAllowed = true;
        if (detailLabelTemplate == null) {
            throw new IllegalArgumentException("detail label template must be provided when allowing attached details field");
        } else {
            this.detailLabelTemplate = detailLabelTemplate;
        }
    }

    public PicklistOptionDef(Long optionId, String stableId, Template optionLabelTemplate, Template tooltipTemplate,
                             Template detailLabelTemplate, boolean isExclusive) {
        this.optionId = optionId;
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.optionLabelTemplate = MiscUtil.checkNonNull(optionLabelTemplate, "optionLabelTemplate");
        this.tooltipTemplate = tooltipTemplate;
        this.isDetailsAllowed = (detailLabelTemplate != null);
        this.detailLabelTemplate = detailLabelTemplate;
        this.isExclusive = isExclusive;
    }

    public PicklistOptionDef(Long optionId, String stableId, Template optionLabelTemplate, Template tooltipTemplate,
                             Template detailLabelTemplate, boolean isExclusive, Template nestedOptionsLabelTemplate,
                             List<PicklistOptionDef> nestedPicklistOptions) {
        this.optionId = optionId;
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.optionLabelTemplate = MiscUtil.checkNonNull(optionLabelTemplate, "optionLabelTemplate");
        this.tooltipTemplate = tooltipTemplate;
        this.isDetailsAllowed = (detailLabelTemplate != null);
        this.detailLabelTemplate = detailLabelTemplate;
        this.isExclusive = isExclusive;
        this.nestedOptionsLabelTemplate = nestedOptionsLabelTemplate;
        this.nestedPicklistOptions = nestedPicklistOptions;
    }

    public PicklistOptionDef(String stableId, Template optionLabelTemplate, Template nestedOptionsLabelTemplate,
                             List<PicklistOptionDef> nestedPicklistOptions) {
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.optionLabelTemplate = MiscUtil.checkNonNull(optionLabelTemplate, "optionLabelTemplate");
        this.nestedOptionsLabelTemplate = nestedOptionsLabelTemplate;
        this.nestedPicklistOptions = nestedPicklistOptions;
    }

    public String getStableId() {
        return stableId;
    }

    public Template getOptionLabelTemplate() {
        return optionLabelTemplate;
    }

    public Template getTooltipTemplate() {
        return tooltipTemplate;
    }

    public Template getDetailLabelTemplate() {
        return detailLabelTemplate;
    }

    public boolean isDetailsAllowed() {
        return isDetailsAllowed;
    }

    public boolean isExclusive() {
        return isExclusive;
    }

    public Long getOptionId() {
        return optionId;
    }

    public void setOptionId(Long optionId) {
        this.optionId = optionId;
    }

    public List<PicklistOptionDef> getNestedPicklistOptions() {
        return nestedPicklistOptions;
    }

    public Template getNestedOptionsLabelTemplate() {
        return nestedOptionsLabelTemplate;
    }

}
