package org.broadinstitute.ddp.model.activity.definition.question;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.util.MiscUtil;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class MatrixGroupDef {

    @NotBlank
    @SerializedName("stableId")
    private String stableId;

    @Valid
    @NotNull
    @SerializedName("nameTemplate")
    private Template nameTemplate;

    private transient Long groupId;

    public MatrixGroupDef(Long groupId, String stableId, Template nameTemplate) {
        this.groupId = groupId;
        this.stableId = MiscUtil.checkNonNull(stableId, "stableId");
        this.nameTemplate = MiscUtil.checkNonNull(nameTemplate, "nameTemplate");
    }

    public MatrixGroupDef(String stableId, Template nameTemplate) {
        this.stableId = MiscUtil.checkNonNull(stableId, "stableId");
        this.nameTemplate = MiscUtil.checkNonNull(nameTemplate, "nameTemplate");
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(long groupId) {
        this.groupId = groupId;
    }

    public String getStableId() {
        return stableId;
    }

    public Template getNameTemplate() {
        return nameTemplate;
    }
}
