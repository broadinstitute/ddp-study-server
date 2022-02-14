package org.broadinstitute.ddp.model.activity.definition.question;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

import javax.validation.Valid;
import java.math.BigInteger;
import java.util.List;

public final class DecimalQuestionDef extends QuestionDef {
    @Valid
    @SerializedName("placeholderTemplate")
    private Template placeholderTemplate;

    @SerializedName("scale")
    private BigInteger scale;

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(String stableId, Template prompt) {
        return new Builder()
                .setStableId(stableId)
                .setPrompt(prompt);
    }

    public DecimalQuestionDef(String stableId, Template promptTemplate, Template placeholderTemplate,
                              boolean isRestricted, boolean hideNumber, boolean writeOnce,
                              Template additionalInfoHeaderTemplate, Template additionalInfoFooterTemplate,
                              List<RuleDef> validations, BigInteger scale) {
        super(QuestionType.DECIMAL, stableId, isRestricted, promptTemplate,
                additionalInfoHeaderTemplate, additionalInfoFooterTemplate, validations, hideNumber, writeOnce);
        this.placeholderTemplate = placeholderTemplate;
        this.scale = scale;
    }

    public Template getPlaceholderTemplate() {
        return placeholderTemplate;
    }

    public BigInteger getScale() {
        return scale;
    }

    public static final class Builder extends AbstractQuestionBuilder<Builder> {
        private Template placeholderTemplate;
        private BigInteger scale;

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

        public Builder setScale(BigInteger scale) {
            this.scale = scale;
            return self();
        }

        public DecimalQuestionDef build() {
            DecimalQuestionDef question = new DecimalQuestionDef(
                    stableId,
                    prompt,
                    placeholderTemplate,
                    isRestricted,
                    hideNumber,
                    writeOnce,
                    getAdditionalInfoHeader(),
                    getAdditionalInfoFooter(),
                    validations,
                    scale);
            configure(question);
            return question;
        }
    }
}
