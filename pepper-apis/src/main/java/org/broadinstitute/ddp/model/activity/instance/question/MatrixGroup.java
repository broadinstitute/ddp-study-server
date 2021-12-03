package org.broadinstitute.ddp.model.activity.instance.question;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.content.Renderable;
import org.broadinstitute.ddp.util.MiscUtil;

import javax.validation.constraints.NotBlank;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

public class MatrixGroup implements Renderable {

    @NotBlank
    @SerializedName("identifier")
    private final String stableId;

    @SerializedName("name")
    private String name;

    private final transient Long nameTemplateId;

    public MatrixGroup(String stableId, Long nameTemplateId) {
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.nameTemplateId = nameTemplateId;
    }

    public String getStableId() {
        return stableId;
    }

    public String getName() {
        return name;
    }

    public Long getNameTemplateId() {
        return nameTemplateId;
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        if (nameTemplateId != null) {
            registry.accept(nameTemplateId);
        }
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        if (nameTemplateId != null) {
            name = rendered.get(nameTemplateId);
            if (name == null) {
                throw new NoSuchElementException("No rendered template found for name with id " + nameTemplateId);
            }

            if (style == ContentStyle.BASIC) {
                name = HtmlConverter.getPlainText(name);
            }
        }
    }
}
