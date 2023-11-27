package org.broadinstitute.ddp.model.activity.instance.question;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.model.activity.instance.answer.EquationAnswer;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

@Getter
public final class EquationQuestion extends Question<EquationAnswer> {
    @SerializedName("placeholderText")
    private String placeholderText;

    @SerializedName("maximumDecimalPlaces")
    private Integer maximumDecimalPlaces;

    @SerializedName("expression")
    private String expression;

    private final transient Long placeholderTemplateId;

    public EquationQuestion(String stableId, long promptTemplateId, Long placeholderTemplateId,
                            boolean isRestricted, boolean isDeprecated, Long tooltipTemplateId,
                            Long additionalInfoHeaderTemplateId, Long additionalInfoFooterTemplateId,
                            List<EquationAnswer> answers, List<Rule<EquationAnswer>> validations,
                            Integer maximumDecimalPlaces, String expression) {
        super(QuestionType.EQUATION, stableId, promptTemplateId, isRestricted, isDeprecated, true, tooltipTemplateId,
                additionalInfoHeaderTemplateId, additionalInfoFooterTemplateId, answers, validations);
        this.placeholderTemplateId = placeholderTemplateId;
        this.maximumDecimalPlaces = maximumDecimalPlaces;
        this.expression = expression;
    }

    @Override
    public void registerTemplateIds(final Consumer<Long> registry) {
        super.registerTemplateIds(registry);
        if (placeholderTemplateId != null) {
            registry.accept(placeholderTemplateId);
        }
    }

    @Override
    public void applyRenderedTemplates(final Provider<String> rendered, final ContentStyle style) {
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
