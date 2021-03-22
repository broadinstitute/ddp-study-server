package org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.impl;

import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistGroup;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistQuestion;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ActivityInstanceAssembleService;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionCreator;

import java.util.ArrayList;
import java.util.List;

import static org.broadinstitute.ddp.service.actvityinstanceassembler.RenderTemplateUtil.renderTemplate;
import static org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionUtil.getAdditionalInfoFooterTemplateId;
import static org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionUtil.getAdditionalInfoHeaderTemplateId;
import static org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionUtil.getPromptTemplateId;
import static org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionUtil.getTooltipTemplateId;
import static org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionUtil.isReadOnly;

/**
 * Creates {@link PicklistQuestion}
 */
public class PicklistQuestionCreator extends ElementCreator {

    public PicklistQuestionCreator(ActivityInstanceAssembleService.Context context) {
        super(context);
    }

    public PicklistQuestion createPicklistQuestion(QuestionCreator questionCreator, PicklistQuestionDef questionDef) {
        PicklistQuestion picklistQuestion = constructPicklistQuestion(questionCreator, questionDef);
        render(picklistQuestion, questionDef);
        return picklistQuestion;
    }

    private PicklistQuestion constructPicklistQuestion(QuestionCreator questionCreator, PicklistQuestionDef questionDef) {
        return new PicklistQuestion(
                questionDef.getStableId(),
                getPromptTemplateId(questionDef),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(context, questionDef),
                getTooltipTemplateId(questionDef),
                getAdditionalInfoHeaderTemplateId(questionDef),
                getAdditionalInfoFooterTemplateId(questionDef),
                questionCreator.getAnswers(PicklistAnswer.class, questionDef.getStableId()),
                questionCreator.getValidationRules(questionDef),
                questionDef.getSelectMode(),
                questionDef.getRenderMode(),
                questionDef.getPicklistLabelTemplate() != null
                        ? questionDef.getPicklistLabelTemplate().getTemplateId() : null,
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

    private void render(PicklistQuestion picklistQuestion, PicklistQuestionDef questionDef) {
        renderTemplate(questionDef.getPicklistLabelTemplate(), picklistQuestion, context);
    }
}
