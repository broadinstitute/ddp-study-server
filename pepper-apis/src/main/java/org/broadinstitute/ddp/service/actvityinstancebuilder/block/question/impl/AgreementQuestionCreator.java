package org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.impl;

import org.broadinstitute.ddp.model.activity.definition.question.AgreementQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.AgreementQuestion;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.QuestionCreator;
import org.broadinstitute.ddp.util.QuestionUtil;

/**
 * Creates {@link AgreementQuestion}
 */
public class AgreementQuestionCreator extends ElementCreator {

    public AgreementQuestionCreator(ActivityInstanceFromDefinitionBuilder.Context context) {
        super(context);
    }

    public AgreementQuestion createAgreementQuestion(QuestionCreator questionCreator, AgreementQuestionDef questionDef) {
        return new AgreementQuestion(
                questionDef.getStableId(),
                renderTemplateIfDefined(questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                QuestionUtil.isReadOnly(context.getFormResponse(), questionDef),
                renderTemplateIfDefined(questionDef.getTooltipTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoHeaderTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(AgreementAnswer.class, questionDef.getStableId()),
                questionCreator.getValidationRules(questionDef)
        );
    }
}
