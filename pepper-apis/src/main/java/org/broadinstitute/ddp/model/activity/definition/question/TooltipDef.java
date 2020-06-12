package org.broadinstitute.ddp.model.activity.definition.question;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.util.MiscUtil;

public class TooltipDef {

    @Valid
    @NotNull
    @SerializedName("textTemplate")
    private Template textTemplate;

    private transient Long tooltipId;

    public TooltipDef(Long tooltipId, Template textTemplate) {
        this.tooltipId = tooltipId;
        this.textTemplate = MiscUtil.checkNonNull(textTemplate, "textTemplate");
    }

    public Long getTooltipId() {
        return tooltipId;
    }

    public void setTooltipId(Long tooltipId) {
        this.tooltipId = tooltipId;
    }

    public Template getTextTemplate() {
        return textTemplate;
    }
}
