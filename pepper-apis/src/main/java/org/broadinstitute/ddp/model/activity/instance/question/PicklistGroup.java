package org.broadinstitute.ddp.model.activity.instance.question;

import java.util.NoSuchElementException;
import java.util.function.Consumer;
import javax.validation.constraints.NotBlank;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.content.Renderable;
import org.broadinstitute.ddp.util.MiscUtil;

public class PicklistGroup implements Renderable {

    @NotBlank
    @SerializedName("identifier")
    private String stableId;

    @SerializedName("name")
    private String name;

    private transient long nameTemplateId;

    public PicklistGroup(String stableId, long nameTemplateId) {
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.nameTemplateId = nameTemplateId;
    }

    public String getStableId() {
        return stableId;
    }

    public String getName() {
        return name;
    }

    public long getNameTemplateId() {
        return nameTemplateId;
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        registry.accept(nameTemplateId);
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        name = rendered.get(nameTemplateId);
        if (name == null) {
            throw new NoSuchElementException("No rendered template found for name with id " + nameTemplateId);
        }

        if (style == ContentStyle.BASIC) {
            name = HtmlConverter.getPlainText(name);
        }
    }
}
