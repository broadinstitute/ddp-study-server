package org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.impl;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistGroup;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistQuestion;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.QuestionCreator;
import org.broadinstitute.ddp.util.QuestionUtil;

/**
 * Creates {@link PicklistQuestion}
 */
public class PicklistQuestionCreator extends ElementCreator {

    public PicklistQuestionCreator(ActivityInstanceFromDefinitionBuilder.Context context) {
        super(context);
    }

    public PicklistQuestion createPicklistQuestion(QuestionCreator questionCreator, PicklistQuestionDef questionDef) {
        return new PicklistQuestion(
                questionDef.getStableId(),
                renderTemplateIfDefined(questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                QuestionUtil.isReadOnly(context.getFormResponse(), questionDef),
                renderTemplateIfDefined(questionDef.getTooltipTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoHeaderTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(PicklistAnswer.class, questionDef.getStableId()),
                questionCreator.getValidationRules(questionDef),
                questionDef.getSelectMode(),
                questionDef.getRenderMode(),
                renderTemplateIfDefined(questionDef.getPicklistLabelTemplate()),
                createPickListOptions(questionDef),
                createPickListGroups(questionDef)
        );
    }

    private List<PicklistOption> createPickListOptions(PicklistQuestionDef questionDef) {
        List<PicklistOption> picklistOptions = new ArrayList<>();
        var picklistOptionCreator = new PicklistOptionCreator(context);
        if (questionDef.getAllPicklistOptions() != null) {
            questionDef.getAllPicklistOptions().forEach(po ->
                    picklistOptions.add(picklistOptionCreator.createPicklistOption(po)));
        }
        return picklistOptions;
    }

    private List<PicklistGroup> createPickListGroups(PicklistQuestionDef questionDef) {
        List<PicklistGroup> picklistGroups = new ArrayList<>();
        var picklistGroupCreator = new PicklistGroupCreator(context);
        if (questionDef.getGroups() != null) {
            questionDef.getGroups().forEach(pg -> picklistGroups.add(picklistGroupCreator.createPicklistOption(pg)));
        }
        return picklistGroups;
    }
}
