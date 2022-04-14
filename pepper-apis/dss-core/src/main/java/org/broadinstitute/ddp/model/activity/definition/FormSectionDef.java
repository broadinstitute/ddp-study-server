package org.broadinstitute.ddp.model.activity.definition;

import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.util.MiscUtil;

public class FormSectionDef {

    @SerializedName("sectionCode")
    private String sectionCode;

    @Valid
    @SerializedName("nameTemplate")
    private Template nameTemplate;

    @SerializedName("icons")
    private List<@Valid @NotNull SectionIcon> icons = new ArrayList<>();

    @NotNull
    @SerializedName("blocks")
    private List<@Valid @NotNull FormBlockDef> blocks;

    private transient Long sectionId;

    public FormSectionDef(String sectionCode, List<FormBlockDef> blocks) {
        this.sectionCode = sectionCode;
        this.blocks = MiscUtil.checkNonNull(blocks, "blocks");
    }

    public FormSectionDef(String sectionCode, Template nameTemplate, List<SectionIcon> icons, List<FormBlockDef> blocks) {
        this.sectionCode = sectionCode;
        this.nameTemplate = nameTemplate;
        this.blocks = MiscUtil.checkNonNull(blocks, "blocks");
        if (icons != null) {
            this.icons.addAll(icons);
        }
    }

    public String getSectionCode() {
        return sectionCode;
    }

    public void setSectionCode(String sectionCode) {
        this.sectionCode = sectionCode;
    }

    public Template getNameTemplate() {
        return nameTemplate;
    }

    public void setNameTemplate(Template nameTemplate) {
        this.nameTemplate = nameTemplate;
    }

    public List<SectionIcon> getIcons() {
        return icons;
    }

    public void addIcon(SectionIcon icon) {
        this.icons.add(icon);
    }

    public boolean hasIcons() {
        return icons != null && !icons.isEmpty();
    }

    public List<FormBlockDef> getBlocks() {
        return blocks;
    }

    public Long getSectionId() {
        return sectionId;
    }

    public void setSectionId(Long sectionId) {
        this.sectionId = sectionId;
    }
}
