package org.broadinstitute.ddp.model.activity.definition.question;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.util.MiscUtil;

public class MatrixGroupDef {

    @NotBlank
    @SerializedName("stableId")
    private final String stableId;

    @Valid
    @SerializedName("nameTemplate")
    private final Template nameTemplate;

    private transient Long groupId;

    public MatrixGroupDef(Long groupId, String stableId, Template nameTemplate) {
        this.groupId = groupId;
        this.stableId = MiscUtil.checkNonNull(stableId, "stableId");
        this.nameTemplate = nameTemplate;
    }

    public MatrixGroupDef(String stableId, Template nameTemplate) {
        this.stableId = MiscUtil.checkNonNull(stableId, "stableId");
        this.nameTemplate = nameTemplate;
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
