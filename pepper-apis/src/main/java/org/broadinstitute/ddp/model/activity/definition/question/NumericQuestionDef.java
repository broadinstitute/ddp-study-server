package org.broadinstitute.ddp.model.activity.definition.question;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.types.NumericType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.util.MiscUtil;

public final class NumericQuestionDef extends QuestionDef {

    @NotNull
    @SerializedName("numericType")
    private NumericType numericType;

    @Valid
    @SerializedName("placeholderTemplate")
    private Template placeholderTemplate;

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(NumericType numericType, String stableId, Template prompt) {
        return new Builder()
                .setNumericType(numericType)
                .setStableId(stableId)
                .setPrompt(prompt);
    }

    public NumericQuestionDef(String stableId, Template promptTemplate, Template placeholderTemplate,
                              boolean isRestricted, boolean hideNumber,
                              Template additionalInfoHeaderTemplate, Template additionalInfoFooterTemplate,
                              List<RuleDef> validations, NumericType numericType) {
        super(QuestionType.NUMERIC, stableId, isRestricted, promptTemplate,
                additionalInfoHeaderTemplate, additionalInfoFooterTemplate, validations, hideNumber);
        this.numericType = MiscUtil.checkNonNull(numericType, "numericType");
        this.placeholderTemplate = placeholderTemplate;
    }

    public NumericType getNumericType() {
        return numericType;
    }

    public Template getPlaceholderTemplate() {
        return placeholderTemplate;
    }

    public static final class Builder extends AbstractQuestionBuilder<Builder> {

        private NumericType numericType;
        private Template placeholderTemplate;

        private Builder() {
            // Use static factories.
        }

        @Override
        protected Builder self() {
            return this;
        }

        public Builder setNumericType(NumericType numericType) {
            this.numericType = numericType;
            return this;
        }

        public Builder setPlaceholderTemplate(Template placeholderTemplate) {
            this.placeholderTemplate = placeholderTemplate;
            return self();
        }

        public NumericQuestionDef build() {
            NumericQuestionDef question = new NumericQuestionDef(
                    stableId,
                    prompt,
                    placeholderTemplate,
                    isRestricted,
                    hideNumber,
                    getAdditionalInfoHeader(),
                    getAdditionalInfoFooter(),
                    validations,
                    numericType);
            configure(question);
            return question;
        }
    }
}
