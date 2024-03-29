package org.broadinstitute.ddp.model.activity.definition;

import java.util.stream.Stream;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.util.MiscUtil;

public final class ContentBlockDef extends FormBlockDef {

    @Valid
    @SerializedName("titleTemplate")
    private Template titleTemplate;

    @Valid
    @NotNull
    @SerializedName("bodyTemplate")
    private Template bodyTemplate;

    private transient Long titleTemplateId;
    private transient long bodyTemplateId;

    public ContentBlockDef(Template bodyTemplate) {
        super(BlockType.CONTENT);
        this.bodyTemplate = bodyTemplate;
    }

    public ContentBlockDef(Template titleTemplate, Template bodyTemplate) {
        super(BlockType.CONTENT);
        this.titleTemplate = titleTemplate;
        this.bodyTemplate = MiscUtil.checkNonNull(bodyTemplate, "bodyTemplate");
    }

    public Template getTitleTemplate() {
        return titleTemplate;
    }

    public Long getTitleTemplateId() {
        return titleTemplateId;
    }

    public void setTitleTemplateId(Long titleTemplateId) {
        this.titleTemplateId = titleTemplateId;
    }

    public Template getBodyTemplate() {
        return bodyTemplate;
    }

    public long getBodyTemplateId() {
        return bodyTemplateId;
    }

    public void setBodyTemplateId(long bodyTemplateId) {
        this.bodyTemplateId = bodyTemplateId;
    }

    @Override
    public Stream<QuestionDef> getQuestions() {
        return Stream.of();
    }
}
