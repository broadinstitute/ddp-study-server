package org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.impl;

import org.broadinstitute.ddp.model.activity.definition.question.FileQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.FileAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.FileQuestion;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.QuestionCreator;
import org.broadinstitute.ddp.util.QuestionUtil;

/**
 * Creates {@link FileQuestion}
 */
public class FileQuestionCreator extends ElementCreator {

    public FileQuestionCreator(ActivityInstanceFromDefinitionBuilder.Context context) {
        super(context);
    }

    public FileQuestion constructFileQuestion(QuestionCreator questionCreator, FileQuestionDef questionDef) {
        return new FileQuestion(
                questionDef.getStableId(),
                renderTemplateIfDefined(questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                QuestionUtil.isReadOnly(context.getFormResponse(), questionDef),
                renderTemplateIfDefined(questionDef.getTooltipTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoHeaderTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(FileAnswer.class, questionDef.getStableId()),
                questionCreator.getValidationRules(questionDef)
        );
    }
}
