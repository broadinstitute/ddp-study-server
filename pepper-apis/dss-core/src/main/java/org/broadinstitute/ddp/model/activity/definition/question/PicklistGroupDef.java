package org.broadinstitute.ddp.model.activity.definition.question;

import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.util.MiscUtil;

public class PicklistGroupDef {

    @NotBlank
    @SerializedName("stableId")
    private String stableId;

    @Valid
    @NotNull
    @SerializedName("nameTemplate")
    private Template nameTemplate;

    @NotEmpty
    @SerializedName("options")
    private List<@Valid @NotNull PicklistOptionDef> options = new ArrayList<>();

    private transient Long groupId;

    public PicklistGroupDef(String stableId, Template nameTemplate, List<PicklistOptionDef> options) {
        this(null, stableId, nameTemplate, options);
    }

    public PicklistGroupDef(Long groupId, String stableId, Template nameTemplate, List<PicklistOptionDef> options) {
        this.groupId = groupId;
        this.stableId = MiscUtil.checkNonNull(stableId, "stableId");
        this.nameTemplate = MiscUtil.checkNonNull(nameTemplate, "nameTemplate");
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("picklist group needs to have at least one option");
        } else {
            this.options.addAll(options);
        }
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getStableId() {
        return stableId;
    }

    public Template getNameTemplate() {
        return nameTemplate;
    }

    public List<PicklistOptionDef> getOptions() {
        return options;
    }
}
