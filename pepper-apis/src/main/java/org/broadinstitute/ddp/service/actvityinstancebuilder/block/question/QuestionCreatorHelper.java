package org.broadinstitute.ddp.service.actvityinstancebuilder.block.question;

import static org.broadinstitute.ddp.util.QuestionUtil.isReadOnly;

import org.broadinstitute.ddp.model.activity.definition.question.AgreementQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.FileQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.NumericQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.FileAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.AgreementQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.BoolQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.CompositeQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.DatePicklistQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.DateQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.FileQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.NumericQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.TextQuestion;
import org.broadinstitute.ddp.service.actvityinstancebuilder.AbstractCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;
import org.broadinstitute.ddp.util.CollectionMiscUtil;

public class QuestionCreatorHelper extends AbstractCreator {

    private final PicklistCreatorHelper picklistCreatorHelper;

    public QuestionCreatorHelper(ActivityInstanceFromDefinitionBuilder.Context context) {
        super(context);
        this.picklistCreatorHelper = new PicklistCreatorHelper(context);
    }

    AgreementQuestion createAgreementQuestion(AgreementQuestionDef questionDef) {
        return new AgreementQuestion(
                questionDef.getStableId(),
                renderTemplateIfDefined(questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, context.getFormResponse().getLatestStatus().getType(), context.getPreviousInstanceId()),
                renderTemplateIfDefined(questionDef.getTooltipTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoHeaderTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoFooterTemplate()),
                context.getQuestionCreator().getAnswers(AgreementAnswer.class, questionDef.getStableId()),
                context.getQuestionCreator().getValidationRules(questionDef)
        );
    }

    BoolQuestion createBoolQuestion(BoolQuestionDef questionDef) {
        return new BoolQuestion(
                questionDef.getStableId(),
                renderTemplateIfDefined(questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, context.getFormResponse().getLatestStatus().getType(), context.getPreviousInstanceId()),
                renderTemplateIfDefined(questionDef.getTooltipTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoHeaderTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoFooterTemplate()),
                context.getQuestionCreator().getAnswers(BoolAnswer.class, questionDef.getStableId()),
                context.getQuestionCreator().getValidationRules(questionDef),
                renderTemplateIfDefined(questionDef.getTrueTemplate()),
                renderTemplateIfDefined(questionDef.getFalseTemplate())
        );
    }

    CompositeQuestion createCompositeQuestion(CompositeQuestionDef questionDef) {
        return new CompositeQuestion(
                questionDef.getStableId(),
                renderTemplateIfDefined(questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, context.getFormResponse().getLatestStatus().getType(), context.getPreviousInstanceId()),
                renderTemplateIfDefined(questionDef.getTooltipTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoHeaderTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoFooterTemplate()),
                context.getQuestionCreator().getValidationRules(questionDef),
                questionDef.isAllowMultiple(),
                questionDef.isUnwrapOnExport(),
                renderTemplateIfDefined(questionDef.getAddButtonTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalItemTemplate()),
                CollectionMiscUtil.createListFromAnotherList(questionDef.getChildren(),
                        (childQuestionDef) -> context.getQuestionCreator().createQuestion(childQuestionDef)),
                questionDef.getChildOrientation(),
                context.getQuestionCreator().getAnswers(CompositeAnswer.class, questionDef.getStableId())
        );
    }

    DatePicklistQuestion createDatePickListQuestion(DateQuestionDef questionDef) {
        return new DatePicklistQuestion(
                questionDef.getStableId(),
                renderTemplateIfDefined(questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, context.getFormResponse().getLatestStatus().getType(), context.getPreviousInstanceId()),
                renderTemplateIfDefined(questionDef.getTooltipTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoHeaderTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoFooterTemplate()),
                context.getQuestionCreator().getAnswers(DateAnswer.class, questionDef.getStableId()),
                context.getQuestionCreator().getValidationRules(questionDef),
                questionDef.getRenderMode(),
                questionDef.isDisplayCalendar(),
                questionDef.getFields(),
                renderTemplateIfDefined(questionDef.getPlaceholderTemplate()),
                questionDef.getPicklistDef().getUseMonthNames(),
                questionDef.getPicklistDef().getStartYear(),
                questionDef.getPicklistDef().getEndYear(),
                questionDef.getPicklistDef().getFirstSelectedYear()
        );
    }

    DateQuestion createDateQuestion(DateQuestionDef questionDef) {
        return new DateQuestion(
                questionDef.getStableId(),
                renderTemplateIfDefined(questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, context.getFormResponse().getLatestStatus().getType(), context.getPreviousInstanceId()),
                renderTemplateIfDefined(questionDef.getTooltipTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoHeaderTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoFooterTemplate()),
                context.getQuestionCreator().getAnswers(DateAnswer.class, questionDef.getStableId()),
                context.getQuestionCreator().getValidationRules(questionDef),
                questionDef.getRenderMode(),
                questionDef.isDisplayCalendar(),
                questionDef.getFields(),
                renderTemplateIfDefined(questionDef.getPlaceholderTemplate())
        );
    }

    FileQuestion constructFileQuestion(FileQuestionDef questionDef) {
        return new FileQuestion(
                questionDef.getStableId(),
                renderTemplateIfDefined(questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, context.getFormResponse().getLatestStatus().getType(), context.getPreviousInstanceId()),
                renderTemplateIfDefined(questionDef.getTooltipTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoHeaderTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoFooterTemplate()),
                context.getQuestionCreator().getAnswers(FileAnswer.class, questionDef.getStableId()),
                context.getQuestionCreator().getValidationRules(questionDef)
        );
    }

    NumericQuestion createNumericQuestion(NumericQuestionDef questionDef) {
        return new NumericQuestion(
                questionDef.getStableId(),
                renderTemplateIfDefined(questionDef.getPromptTemplate()),
                renderTemplateIfDefined(questionDef.getPlaceholderTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, context.getFormResponse().getLatestStatus().getType(), context.getPreviousInstanceId()),
                renderTemplateIfDefined(questionDef.getTooltipTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoHeaderTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoFooterTemplate()),
                context.getQuestionCreator().getAnswers(NumericAnswer.class, questionDef.getStableId()),
                context.getQuestionCreator().getValidationRules(questionDef),
                questionDef.getNumericType()
        );
    }

    PicklistQuestion createPicklistQuestion(PicklistQuestionDef questionDef) {
        return new PicklistQuestion(
                questionDef.getStableId(),
                renderTemplateIfDefined(questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, context.getFormResponse().getLatestStatus().getType(), context.getPreviousInstanceId()),
                renderTemplateIfDefined(questionDef.getTooltipTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoHeaderTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoFooterTemplate()),
                context.getQuestionCreator().getAnswers(PicklistAnswer.class, questionDef.getStableId()),
                context.getQuestionCreator().getValidationRules(questionDef),
                questionDef.getSelectMode(),
                questionDef.getRenderMode(),
                renderTemplateIfDefined(questionDef.getPicklistLabelTemplate()),
                CollectionMiscUtil.createListFromAnotherList(questionDef.getAllPicklistOptions(),
                        (picklistOptionDef) -> picklistCreatorHelper.createPicklistOption(picklistOptionDef)),
                CollectionMiscUtil.createListFromAnotherList(questionDef.getGroups(),
                        (picklistGroupDef) -> picklistCreatorHelper.createPicklistOption(picklistGroupDef))
        );
    }

    TextQuestion createTextQuestion(TextQuestionDef questionDef) {
        return new TextQuestion(
                questionDef.getStableId(),
                renderTemplateIfDefined(questionDef.getPromptTemplate()),
                renderTemplateIfDefined(questionDef.getPlaceholderTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, context.getFormResponse().getLatestStatus().getType(), context.getPreviousInstanceId()),
                renderTemplateIfDefined(questionDef.getTooltipTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoHeaderTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoFooterTemplate()),
                context.getQuestionCreator().getAnswers(TextAnswer.class, questionDef.getStableId()),
                context.getQuestionCreator().getValidationRules(questionDef),
                questionDef.getInputType(),
                questionDef.getSuggestionType(),
                questionDef.getSuggestions(),
                questionDef.isConfirmEntry(),
                renderTemplateIfDefined(questionDef.getConfirmPromptTemplate()),
                renderTemplateIfDefined(questionDef.getMismatchMessageTemplate())
        );
    }
}
