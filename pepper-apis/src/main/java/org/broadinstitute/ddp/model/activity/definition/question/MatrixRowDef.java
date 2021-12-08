package org.broadinstitute.ddp.model.activity.definition.question;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.util.MiscUtil;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class MatrixRowDef {

    @NotBlank
    @SerializedName("stableId")
    private String stableId;

    @Valid
    @NotNull
    @SerializedName("rowLabelTemplate")
    private Template rowLabelTemplate;

    @Valid
    @SerializedName("tooltipTemplate")
    private Template tooltipTemplate;

    private transient Long rowId;

    public MatrixRowDef(Long rowId, String stableId, Template rowLabelTemplate, Template tooltipTemplate) {
        this.rowId = rowId;
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.rowLabelTemplate = MiscUtil.checkNonNull(rowLabelTemplate, "rowLabelTemplate");
        this.tooltipTemplate = tooltipTemplate;
    }

    public MatrixRowDef(String stableId, Template rowLabelTemplate) {
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.rowLabelTemplate = MiscUtil.checkNonNull(rowLabelTemplate, "rowLabelTemplate");
    }

    public String getStableId() {
        return stableId;
    }

    public void setStableId(String stableId) {
        this.stableId = stableId;
    }

    public Template getRowLabelTemplate() {
        return rowLabelTemplate;
    }

    public void setRowLabelTemplate(Template rowLabelTemplate) {
        this.rowLabelTemplate = rowLabelTemplate;
    }

    public Template getTooltipTemplate() {
        return tooltipTemplate;
    }

    public void setTooltipTemplate(Template tooltipTemplate) {
        this.tooltipTemplate = tooltipTemplate;
    }

    public Long getRowId() {
        return rowId;
    }

    public void setRowId(Long questionId) {
        this.rowId = questionId;
    }
}
