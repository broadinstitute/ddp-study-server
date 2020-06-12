package org.broadinstitute.ddp.model.activity.instance.question;

import java.util.NoSuchElementException;
import java.util.function.Consumer;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.content.Renderable;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class Tooltip implements Renderable {

    @SerializedName("text")
    private String text;

    private transient long tooltipId;
    private transient long textTemplateId;

    @JdbiConstructor
    public Tooltip(
            @ColumnName("tooltip_id") long tooltipId,
            @ColumnName("text_template_id") long textTemplateId) {
        this.tooltipId = tooltipId;
        this.textTemplateId = textTemplateId;
    }

    public long getTooltipId() {
        return tooltipId;
    }

    public long getTextTemplateId() {
        return textTemplateId;
    }

    public String getText() {
        return text;
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        registry.accept(textTemplateId);
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        text = rendered.get(textTemplateId);
        if (text == null) {
            throw new NoSuchElementException("No rendered template found for tooltip text template with id " + textTemplateId);
        }
        // Only plain text is supported.
        text = HtmlConverter.getPlainText(text);
    }
}
