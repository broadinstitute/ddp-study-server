package org.broadinstitute.ddp.model.activity.instance;

import static org.broadinstitute.ddp.constants.SqlConstants.FormSectionTable;

import java.beans.ConstructorProperties;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.content.Renderable;
import org.broadinstitute.ddp.model.activity.definition.SectionIcon;

public class FormSection implements Renderable {

    @SerializedName("name")
    private String name;

    @SerializedName("icons")
    private Collection<SectionIcon> icons = new ArrayList<>();

    @NotNull
    @SerializedName("blocks")
    private final List<FormBlock> blocks = new ArrayList<>();

    private transient Long nameTemplateId;

    @ConstructorProperties({FormSectionTable.NAME_TEMPLATE_ID})
    public FormSection(Long nameTemplateId) {
        this.nameTemplateId = nameTemplateId;
    }

    public FormSection(List<FormBlock> blocks) {
        if (blocks != null) {
            this.blocks.addAll(blocks);
        }
    }

    public Long getNameTemplateId() {
        return nameTemplateId;
    }

    public String getName() {
        return name;
    }

    public Collection<SectionIcon> getIcons() {
        return icons;
    }

    public void addIcon(SectionIcon icon) {
        icons.add(icon);
    }

    public SectionIcon getIconById(long iconId) {
        for (SectionIcon icon : icons) {
            if (icon.getIconId() != null && icon.getIconId() == iconId) {
                return icon;
            }
        }
        return null;
    }

    public List<FormBlock> getBlocks() {
        return blocks;
    }

    public void addBlocks(List<FormBlock> blocks) {
        if (blocks != null) {
            this.blocks.addAll(blocks);
        }
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        if (nameTemplateId != null) {
            registry.accept(nameTemplateId);
        }
        for (FormBlock block : blocks) {
            block.registerTemplateIds(registry);
        }
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        if (nameTemplateId != null) {
            name = rendered.get(nameTemplateId);
            if (name == null) {
                throw new NoSuchElementException("No rendered template found for section name template with id " + nameTemplateId);
            }
        }

        if (style == ContentStyle.BASIC) {
            name = HtmlConverter.getPlainText(name);
        }

        for (FormBlock block : blocks) {
            block.applyRenderedTemplates(rendered, style);
        }
    }
}
