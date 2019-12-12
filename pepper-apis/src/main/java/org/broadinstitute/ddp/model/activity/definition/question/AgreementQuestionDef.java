package org.broadinstitute.ddp.model.activity.definition.question;

import java.util.List;

import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public final class AgreementQuestionDef extends QuestionDef {

    public AgreementQuestionDef(
            String stableId,
            boolean isRestricted,
            Template promptTemplate,
            Template additionalInfoHeaderTemplate,
            Template additionalInfoFooterTemplate,
            List<RuleDef> validations,
            boolean hideNumber
    ) {
        super(QuestionType.AGREEMENT,
                stableId,
                isRestricted,
                promptTemplate,
                additionalInfoHeaderTemplate,
                additionalInfoFooterTemplate,
                validations,
                hideNumber);
    }

    public void setDeprecated(boolean deprecated) {
        isDeprecated = deprecated;
    }
}
