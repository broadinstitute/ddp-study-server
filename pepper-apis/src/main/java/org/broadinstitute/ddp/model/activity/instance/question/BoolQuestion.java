package org.broadinstitute.ddp.model.activity.instance.question;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public final class BoolQuestion extends Question<BoolAnswer> {

    @NotNull
    @SerializedName("trueContent")
    private String trueContent;

    @NotNull
    @SerializedName("falseContent")
    private String falseContent;

    private transient long trueTemplateId;
    private transient long falseTemplateId;

    public BoolQuestion(String stableId, long promptTemplateId,
            boolean isRestricted, boolean isDeprecated, Tooltip tooltip,
            Long additionalInfoHeaderTemplateId, Long additionalInfoFooterTemplateId,
            List<BoolAnswer> answers, List<Rule<BoolAnswer>> validations,
            long trueTemplateId, long falseTemplateId) {
        super(QuestionType.BOOLEAN,
                stableId,
                promptTemplateId,
                isRestricted,
                isDeprecated,
                tooltip,
                additionalInfoHeaderTemplateId,
                additionalInfoFooterTemplateId,
                answers,
                validations);
        this.trueTemplateId = trueTemplateId;
        this.falseTemplateId = falseTemplateId;

    }

    public BoolQuestion(String stableId, long promptTemplateId,
                        List<BoolAnswer> answers, List<Rule<BoolAnswer>> validations,
                        long trueTemplateId, long falseTemplateId) {
        this(stableId,
                promptTemplateId,
                false,
                false,
                null,
                null,
                null,
                answers,
                validations,
                trueTemplateId,
                falseTemplateId);
    }

    public String getTrueContent() {
        return trueContent;
    }

    public String getFalseContent() {
        return falseContent;
    }

    public long getTrueTemplateId() {
        return trueTemplateId;
    }

    public long getFalseTemplateId() {
        return falseTemplateId;
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        super.registerTemplateIds(registry);
        registry.accept(trueTemplateId);
        registry.accept(falseTemplateId);
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        super.applyRenderedTemplates(rendered, style);

        trueContent = rendered.get(trueTemplateId);
        if (trueContent == null) {
            throw new NoSuchElementException("No rendered template for true content with id " + trueTemplateId);
        }

        falseContent = rendered.get(falseTemplateId);
        if (falseContent == null) {
            throw new NoSuchElementException("No rendered template for false content with id " + falseTemplateId);
        }

        if (style == ContentStyle.BASIC) {
            trueContent = HtmlConverter.getPlainText(trueContent);
            falseContent = HtmlConverter.getPlainText(falseContent);
        }
    }
}
