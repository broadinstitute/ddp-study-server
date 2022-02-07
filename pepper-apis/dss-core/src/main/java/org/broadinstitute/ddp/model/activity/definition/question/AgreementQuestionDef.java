package org.broadinstitute.ddp.model.activity.definition.question;

import java.util.List;

import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public final class AgreementQuestionDef extends QuestionDef {

    public static Builder builder(String stableId, Template prompt) {
        return new Builder()
                .setStableId(stableId)
                .setPrompt(prompt);
    }

    public AgreementQuestionDef(
            String stableId,
            boolean isRestricted,
            Template promptTemplate,
            Template tooltipTemplate,
            Template additionalInfoHeaderTemplate,
            Template additionalInfoFooterTemplate,
            List<RuleDef> validations,
            boolean hideNumber,
            boolean writeOnce
    ) {
        super(QuestionType.AGREEMENT,
                stableId,
                isRestricted,
                promptTemplate,
                additionalInfoHeaderTemplate,
                additionalInfoFooterTemplate,
                validations,
                hideNumber,
                writeOnce);
        this.tooltipTemplate = tooltipTemplate;
    }

    public void setDeprecated(boolean deprecated) {
        isDeprecated = deprecated;
    }

    public static final class Builder extends AbstractQuestionBuilder<Builder> {
        private Builder() {
            // Use static factories.
        }

        @Override
        protected Builder self() {
            return this;
        }

        public AgreementQuestionDef build() {
            var question = new AgreementQuestionDef(
                    stableId,
                    isRestricted,
                    prompt,
                    tooltip,
                    getAdditionalInfoHeader(),
                    getAdditionalInfoFooter(),
                    validations,
                    hideNumber,
                    writeOnce);
            configure(question);
            return question;
        }
    }
}
