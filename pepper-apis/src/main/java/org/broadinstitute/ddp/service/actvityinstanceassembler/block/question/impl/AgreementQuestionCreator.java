package org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.impl;

import org.broadinstitute.ddp.model.activity.definition.question.AgreementQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.AgreementQuestion;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ActivityInstanceAssembleService;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionCreator;

import static org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionUtil.isReadOnly;

/**
 * Creates {@link AgreementQuestion}
 */
public class AgreementQuestionCreator extends ElementCreator {

    public AgreementQuestionCreator(ActivityInstanceAssembleService.Context context) {
        super(context);
    }

    public AgreementQuestion createAgreementQuestion(QuestionCreator questionCreator, AgreementQuestionDef questionDef) {
        return new AgreementQuestion(
                questionDef.getStableId(),
                getTemplateId(questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(context, questionDef),
                getTemplateId(questionDef.getTooltipTemplate()),
                getTemplateId(questionDef.getAdditionalInfoHeaderTemplate()),
                getTemplateId(questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(AgreementAnswer.class, questionDef.getStableId()),
                questionCreator.getValidationRules(questionDef)
        );
    }
}
