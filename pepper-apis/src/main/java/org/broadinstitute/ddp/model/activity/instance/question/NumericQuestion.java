package org.broadinstitute.ddp.model.activity.instance.question;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericAnswer;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.types.NumericType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.util.MiscUtil;

public final class NumericQuestion extends Question<NumericAnswer> {

    @NotNull
    @SerializedName("numericType")
    private NumericType numericType;

    @SerializedName("placeholderText")
    private String placeholderText;

    private transient Long placeholderTemplateId;

    public NumericQuestion(String stableId, long promptTemplateId, Long placeholderTemplateId,
                           boolean isRestricted, boolean isDeprecated, Boolean readonly, Long tooltipTemplateId,
                           Long additionalInfoHeaderTemplateId, Long additionalInfoFooterTemplateId,
                           List<NumericAnswer> answers, List<Rule<NumericAnswer>> validations,
                           NumericType numericType) {
        super(QuestionType.NUMERIC, stableId, promptTemplateId, isRestricted, isDeprecated, readonly, tooltipTemplateId,
                additionalInfoHeaderTemplateId, additionalInfoFooterTemplateId, answers, validations);
        this.numericType = MiscUtil.checkNonNull(numericType, "numericType");
        this.placeholderTemplateId = placeholderTemplateId;
    }

    public NumericType getNumericType() {
        return numericType;
    }

    public Long getPlaceholderTemplateId() {
        return placeholderTemplateId;
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        super.registerTemplateIds(registry);
        if (placeholderTemplateId != null) {
            registry.accept(placeholderTemplateId);
        }
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        super.applyRenderedTemplates(rendered, style);
        if (placeholderTemplateId != null) {
            placeholderText = rendered.get(placeholderTemplateId);
            if (placeholderText == null) {
                throw new NoSuchElementException("No rendered template found for placeholder with id " + placeholderTemplateId);
            }
            if (style == ContentStyle.BASIC) {
                placeholderText = HtmlConverter.getPlainText(placeholderText);
            }
        }
    }
}
