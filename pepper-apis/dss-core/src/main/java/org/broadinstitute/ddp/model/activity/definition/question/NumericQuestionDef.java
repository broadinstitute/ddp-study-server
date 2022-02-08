package org.broadinstitute.ddp.model.activity.definition.question;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

import javax.validation.Valid;
import java.util.List;

public final class NumericQuestionDef extends QuestionDef {
    @Valid
    @SerializedName("placeholderTemplate")
    private Template placeholderTemplate;

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(String stableId, Template prompt) {
        return new Builder()
                .setStableId(stableId)
                .setPrompt(prompt);
    }

    public NumericQuestionDef(String stableId, Template promptTemplate, Template placeholderTemplate,
                              boolean isRestricted, boolean hideNumber, boolean writeOnce,
                              Template additionalInfoHeaderTemplate, Template additionalInfoFooterTemplate,
                              List<RuleDef> validations) {
        super(QuestionType.NUMERIC, stableId, isRestricted, promptTemplate,
                additionalInfoHeaderTemplate, additionalInfoFooterTemplate, validations, hideNumber, writeOnce);
        this.placeholderTemplate = placeholderTemplate;
    }

    public Template getPlaceholderTemplate() {
        return placeholderTemplate;
    }

    public static final class Builder extends AbstractQuestionBuilder<Builder> {
        private Template placeholderTemplate;

        private Builder() {
            // Use static factories.
        }

        @Override
        protected Builder self() {
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
                    writeOnce,
                    getAdditionalInfoHeader(),
                    getAdditionalInfoFooter(),
                    validations);
            configure(question);
            return question;
        }
    }
}
