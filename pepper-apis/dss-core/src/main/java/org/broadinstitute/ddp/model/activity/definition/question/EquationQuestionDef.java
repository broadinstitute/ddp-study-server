package org.broadinstitute.ddp.model.activity.definition.question;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.broadinstitute.ddp.equation.ValidEquation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;

import javax.validation.Valid;

@Getter
@SuperBuilder(toBuilder = true)
public final class EquationQuestionDef extends QuestionDef {
    @Valid
    @SerializedName("placeholderTemplate")
    private final Template placeholderTemplate;

    @SerializedName("maximumDecimalPlaces")
    private final Integer maximumDecimalPlaces;

    @ValidEquation
    @SerializedName("expression")
    private final String expression;
}
