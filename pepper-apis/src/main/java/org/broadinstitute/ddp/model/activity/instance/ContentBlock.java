package org.broadinstitute.ddp.model.activity.instance;

import java.util.NoSuchElementException;
import java.util.function.Consumer;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.model.activity.types.BlockType;

public final class ContentBlock extends FormBlock {

    @SerializedName("title")
    private String title;

    @NotNull
    @SerializedName("body")
    private String body;

    private transient Long titleTemplateId;
    private transient long bodyTemplateId;

    public ContentBlock(long bodyTemplateId) {
        super(BlockType.CONTENT);
        this.titleTemplateId = null;
        this.bodyTemplateId = bodyTemplateId;
    }

    public ContentBlock(Long titleTemplateId, long bodyTemplateId) {
        super(BlockType.CONTENT);
        this.titleTemplateId = titleTemplateId;
        this.bodyTemplateId = bodyTemplateId;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public Long getTitleTemplateId() {
        return titleTemplateId;
    }

    public long getBodyTemplateId() {
        return bodyTemplateId;
    }

    @Override
    public boolean isComplete() {
        // Nothing to check here, just a block of content.
        return true;
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        registry.accept(bodyTemplateId);
        if (titleTemplateId != null) {
            registry.accept(titleTemplateId);
        }
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        body = rendered.get(bodyTemplateId);
        if (body == null) {
            throw new NoSuchElementException("No rendered template found for body template with id " + bodyTemplateId);
        }
        if (titleTemplateId != null) {
            title = rendered.get(titleTemplateId);
            if (title == null) {
                throw new NoSuchElementException("No rendered template found for title template with id " + titleTemplateId);
            }
        }

        if (style == ContentStyle.BASIC) {
            title = HtmlConverter.getPlainText(title);
        }
    }
}
