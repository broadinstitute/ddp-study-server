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

    private transient String groupId;

    public MatrixGroupDef(String groupId, String stableId, Template nameTemplate) {
        this.groupId = groupId;
        this.stableId = MiscUtil.checkNonNull(stableId, "stableId");
        this.nameTemplate = MiscUtil.checkNonNull(nameTemplate, "nameTemplate");
    }

    public String getGroupId() {
        return groupId;
    }

    public String getStableId() {
        return stableId;
    }

    public Template getNameTemplate() {
        return nameTemplate;
    }
}
