package org.broadinstitute.ddp.model.activity.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.broadinstitute.ddp.model.activity.types.PresentationHint;

public final class GroupBlock extends FormBlock {

    @NotNull
    @SerializedName("listStyleHint")
    private ListStyleHint listStyleHint = ListStyleHint.NONE;

    @NotNull
    @SerializedName("presentation")
    private PresentationHint presentationHint = PresentationHint.DEFAULT;

    @SerializedName("title")
    private String title;

    @NotNull
    @SerializedName("nested")
    private List<FormBlock> nested = new ArrayList<>();

    private transient Long titleTemplateId;

    public GroupBlock(ListStyleHint listStyleHint, Long titleTemplateId) {
        super(BlockType.GROUP);
        if (listStyleHint != null) {
            this.listStyleHint = listStyleHint;
        }
        this.titleTemplateId = titleTemplateId;
    }

    public GroupBlock(ListStyleHint listStyleHint, PresentationHint presentationHint, Long titleTemplateId) {
        this(listStyleHint, titleTemplateId);
        this.presentationHint = presentationHint;
    }

    public ListStyleHint getListStyleHint() {
        return listStyleHint;
    }

    public PresentationHint getPresentationHint() {
        return presentationHint;
    }

    public String getTitle() {
        return title;
    }

    public Long getTitleTemplateId() {
        return titleTemplateId;
    }

    public List<FormBlock> getNested() {
        return nested;
    }

    @Override
    public boolean isComplete() {
        if (!shown) {
            return true;
        }
        for (FormBlock child : nested) {
            if (!child.isComplete()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        if (titleTemplateId != null) {
            registry.accept(titleTemplateId);
        }
        for (FormBlock child : nested) {
            child.registerTemplateIds(registry);
        }
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        if (titleTemplateId != null) {
            title = rendered.get(titleTemplateId);
            if (title == null) {
                throw new NoSuchElementException("No rendered template found for title with id " + titleTemplateId);
            }
        }

        if (style == ContentStyle.BASIC) {
            title = HtmlConverter.getPlainText(title);
        }

        for (FormBlock child : nested) {
            child.applyRenderedTemplates(rendered, style);
        }
    }
}
