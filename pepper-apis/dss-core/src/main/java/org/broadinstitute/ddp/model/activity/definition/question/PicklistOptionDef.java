package org.broadinstitute.ddp.model.activity.definition.question;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.util.MiscUtil;

@Getter
@SuperBuilder(toBuilder = true)
public class PicklistOptionDef {
    private transient Long optionId;

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

    @Accessors(fluent = true)
    @SerializedName("allowDetails")
    private boolean isDetailsAllowed;

    @Accessors(fluent = true)
    @SerializedName("exclusive")
    private boolean isExclusive;

    @Accessors(fluent = true)
    @SerializedName("default")
    private boolean isDefault;

    @SerializedName("nestedOptionsLabelTemplate")
    private Template nestedOptionsLabelTemplate;

    @SerializedName("value")
    private String value;

    @SerializedName("nestedOptions")
    private List<@Valid @NotNull PicklistOptionDef> nestedOptions = new ArrayList<>();

    public PicklistOptionDef(String stableId, Template optionLabelTemplate) {
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.optionLabelTemplate = MiscUtil.checkNonNull(optionLabelTemplate, "optionLabelTemplate");
        this.isDetailsAllowed = false;
    }

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
                             Template detailLabelTemplate, boolean isExclusive, boolean isDefault) {
        this.optionId = optionId;
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.optionLabelTemplate = MiscUtil.checkNonNull(optionLabelTemplate, "optionLabelTemplate");
        this.tooltipTemplate = tooltipTemplate;
        this.isDetailsAllowed = (detailLabelTemplate != null);
        this.detailLabelTemplate = detailLabelTemplate;
        this.isExclusive = isExclusive;
        this.isDefault = isDefault;
    }

    public PicklistOptionDef(Long optionId, String stableId, Template optionLabelTemplate, Template tooltipTemplate,
                             Template detailLabelTemplate, boolean isExclusive,
                             boolean isDefault, Template nestedOptionsLabelTemplate,
                             List<PicklistOptionDef> nestedOptions) {
        this.optionId = optionId;
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.optionLabelTemplate = MiscUtil.checkNonNull(optionLabelTemplate, "optionLabelTemplate");
        this.tooltipTemplate = tooltipTemplate;
        this.isDetailsAllowed = (detailLabelTemplate != null);
        this.detailLabelTemplate = detailLabelTemplate;
        this.isExclusive = isExclusive;
        this.isDefault = isDefault;
        this.nestedOptionsLabelTemplate = nestedOptionsLabelTemplate;
        this.nestedOptions = nestedOptions;
    }

    public PicklistOptionDef(String stableId, Template optionLabelTemplate, Template nestedOptionsLabelTemplate,
                             List<PicklistOptionDef> nestedOptions) {
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.optionLabelTemplate = MiscUtil.checkNonNull(optionLabelTemplate, "optionLabelTemplate");
        this.nestedOptionsLabelTemplate = nestedOptionsLabelTemplate;
        this.nestedOptions = nestedOptions;
    }

    public void setOptionId(Long optionId) {
        this.optionId = optionId;
    }
}
