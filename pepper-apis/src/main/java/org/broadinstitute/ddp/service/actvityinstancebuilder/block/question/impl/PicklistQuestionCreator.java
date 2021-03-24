package org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.impl;

import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistGroup;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistQuestion;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromActivityDefStoreBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.QuestionCreator;

import java.util.ArrayList;
import java.util.List;

import static org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.QuestionUtil.isReadOnly;

/**
 * Creates {@link PicklistQuestion}
 */
public class PicklistQuestionCreator extends ElementCreator {

    public PicklistQuestionCreator(ActivityInstanceFromActivityDefStoreBuilder.Context context) {
        super(context);
    }

    public PicklistQuestion createPicklistQuestion(QuestionCreator questionCreator, PicklistQuestionDef questionDef) {
        return new PicklistQuestion(
                questionDef.getStableId(),
                getTemplateId(questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(context, questionDef),
                getTemplateId(questionDef.getTooltipTemplate()),
                getTemplateId(questionDef.getAdditionalInfoHeaderTemplate()),
                getTemplateId(questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(PicklistAnswer.class, questionDef.getStableId()),
                questionCreator.getValidationRules(questionDef),
                questionDef.getSelectMode(),
                questionDef.getRenderMode(),
                getTemplateId(questionDef.getPicklistLabelTemplate()),
                createPickListOptions(questionDef),
                createPickListGroups(questionDef)
        );
    }

    private List<PicklistOption> createPickListOptions(PicklistQuestionDef questionDef) {
        List<PicklistOption> picklistOptions = new ArrayList<>();
        PicklistOptionCreator picklistOptionCreator = new PicklistOptionCreator(context);
        if (questionDef.getAllPicklistOptions() != null) {
            questionDef.getAllPicklistOptions().forEach(po ->
                    picklistOptions.add(picklistOptionCreator.createPicklistOption(po)));
        }
        return picklistOptions;
    }

    private List<PicklistGroup> createPickListGroups(PicklistQuestionDef questionDef) {
        List<PicklistGroup> picklistGroups = new ArrayList<>();
        PicklistGroupCreator picklistGroupCreator = new PicklistGroupCreator(context);
        if (questionDef.getGroups() != null) {
            questionDef.getGroups().forEach(pg -> picklistGroups.add(picklistGroupCreator.createPicklistOption(pg)));
        }
        return picklistGroups;
    }
}
