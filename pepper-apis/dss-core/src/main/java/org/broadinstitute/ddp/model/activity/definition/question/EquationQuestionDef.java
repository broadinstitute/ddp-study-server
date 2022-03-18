package org.broadinstitute.ddp.model.activity.definition.question;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

import javax.validation.Valid;
import java.util.List;

@Getter
@SuperBuilder(toBuilder = true)
public final class EquationQuestionDef extends QuestionDef {
    @Valid
    @SerializedName("placeholderTemplate")
    private final Template placeholderTemplate;

    @SerializedName("maximumDecimalPlaces")
    private final Integer maximumDecimalPlaces;

    @SerializedName("expression")
    private final String expression;

    public EquationQuestionDef(String stableId, Template promptTemplate, Template placeholderTemplate,
                               boolean isRestricted, boolean hideNumber, boolean writeOnce,
                               Template additionalInfoHeaderTemplate, Template additionalInfoFooterTemplate,
                               List<RuleDef> validations, Integer maximumDecimalPlaces, String expression) {
        super(QuestionType.EQUATION, stableId, isRestricted, promptTemplate,
                additionalInfoHeaderTemplate, additionalInfoFooterTemplate, validations, hideNumber, writeOnce);
        this.placeholderTemplate = placeholderTemplate;
        this.maximumDecimalPlaces = maximumDecimalPlaces;
        this.expression = expression;
    }
}
