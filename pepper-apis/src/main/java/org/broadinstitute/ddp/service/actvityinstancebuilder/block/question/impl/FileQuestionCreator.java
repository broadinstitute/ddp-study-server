package org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.impl;

import org.broadinstitute.ddp.model.activity.definition.question.FileQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.FileAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.FileQuestion;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromActivityDefStoreBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.QuestionCreator;

import static org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.QuestionUtil.isReadOnly;

/**
 * Creates {@link FileQuestion}
 */
public class FileQuestionCreator extends ElementCreator {

    public FileQuestionCreator(ActivityInstanceFromActivityDefStoreBuilder.Context context) {
        super(context);
    }

    public FileQuestion constructFileQuestion(QuestionCreator questionCreator, FileQuestionDef questionDef) {
        return new FileQuestion(
                questionDef.getStableId(),
                getTemplateId(questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(context, questionDef),
                getTemplateId(questionDef.getTooltipTemplate()),
                getTemplateId(questionDef.getAdditionalInfoHeaderTemplate()),
                getTemplateId(questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(FileAnswer.class, questionDef.getStableId()),
                questionCreator.getValidationRules(questionDef)
        );
    }
}
