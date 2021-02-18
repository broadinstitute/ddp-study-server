package org.broadinstitute.ddp.model.activity.definition.question;

import java.util.List;

import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public final class FileQuestionDef extends QuestionDef {

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(String stableId, Template prompt) {
        return new Builder()
                .setStableId(stableId)
                .setPrompt(prompt);
    }

    public FileQuestionDef(String stableId, boolean isRestricted, Template promptTemplate,
                           Template additionalInfoHeaderTemplate, Template additionalInfoFooterTemplate,
                           List<RuleDef> validations, boolean hideNumber, boolean writeOnce) {
        super(QuestionType.FILE, stableId, isRestricted, promptTemplate,
                additionalInfoHeaderTemplate, additionalInfoFooterTemplate,
                validations, hideNumber, writeOnce);
    }

    public static final class Builder extends AbstractQuestionBuilder<Builder> {

        private Builder() {
            // Use static factories.
        }

        @Override
        protected Builder self() {
            return this;
        }

        public FileQuestionDef build() {
            FileQuestionDef question = new FileQuestionDef(
                    stableId,
                    isRestricted,
                    prompt,
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
