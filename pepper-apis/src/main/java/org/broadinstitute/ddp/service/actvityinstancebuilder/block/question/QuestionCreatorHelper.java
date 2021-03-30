package org.broadinstitute.ddp.service.actvityinstancebuilder.block.question;

import static org.broadinstitute.ddp.service.actvityinstancebuilder.TemplateHandler.addAndRenderTemplate;
import static org.broadinstitute.ddp.util.QuestionUtil.isReadOnly;

import org.broadinstitute.ddp.model.activity.definition.question.AgreementQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.FileQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.NumericQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.question.AgreementQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.BoolQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.CompositeQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.DatePicklistQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.DateQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.FileQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.NumericQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.TextQuestion;
import org.broadinstitute.ddp.service.actvityinstancebuilder.Context;
import org.broadinstitute.ddp.util.CollectionMiscUtil;

public class QuestionCreatorHelper {

    AgreementQuestion createAgreementQuestion(Context ctx, AgreementQuestionDef questionDef) {
        return new AgreementQuestion(
                questionDef.getStableId(),
                addAndRenderTemplate(ctx, questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                addAndRenderTemplate(ctx, questionDef.getTooltipTemplate()),
                addAndRenderTemplate(ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                addAndRenderTemplate(ctx, questionDef.getAdditionalInfoFooterTemplate()),
                ctx.creators().getQuestionCreator().getAnswers(ctx, questionDef.getStableId()),
                ctx.creators().getQuestionCreator().getValidationRules(ctx, questionDef)
        );
    }

    BoolQuestion createBoolQuestion(Context ctx, BoolQuestionDef questionDef) {
        return new BoolQuestion(
                questionDef.getStableId(),
                addAndRenderTemplate(ctx, questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                addAndRenderTemplate(ctx, questionDef.getTooltipTemplate()),
                addAndRenderTemplate(ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                addAndRenderTemplate(ctx, questionDef.getAdditionalInfoFooterTemplate()),
                ctx.creators().getQuestionCreator().getAnswers(ctx, questionDef.getStableId()),
                ctx.creators().getQuestionCreator().getValidationRules(ctx, questionDef),
                addAndRenderTemplate(ctx, questionDef.getTrueTemplate()),
                addAndRenderTemplate(ctx, questionDef.getFalseTemplate())
        );
    }

    CompositeQuestion createCompositeQuestion(Context ctx, CompositeQuestionDef questionDef) {
        QuestionCreator questionCreator = ctx.creators().getQuestionCreator();
        return new CompositeQuestion(
                questionDef.getStableId(),
                addAndRenderTemplate(ctx, questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                addAndRenderTemplate(ctx, questionDef.getTooltipTemplate()),
                addAndRenderTemplate(ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                addAndRenderTemplate(ctx, questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getValidationRules(ctx, questionDef),
                questionDef.isAllowMultiple(),
                questionDef.isUnwrapOnExport(),
                addAndRenderTemplate(ctx, questionDef.getAddButtonTemplate()),
                addAndRenderTemplate(ctx, questionDef.getAdditionalItemTemplate()),
                CollectionMiscUtil.createListFromAnotherList(questionDef.getChildren(),
                        (childQuestionDef) -> questionCreator.createQuestion(ctx, childQuestionDef)),
                questionDef.getChildOrientation(),
                questionCreator.getAnswers(ctx, questionDef.getStableId())
        );
    }

    DatePicklistQuestion createDatePickListQuestion(Context ctx, DateQuestionDef questionDef) {
        QuestionCreator questionCreator = ctx.creators().getQuestionCreator();
        return new DatePicklistQuestion(
                questionDef.getStableId(),
                addAndRenderTemplate(ctx, questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                addAndRenderTemplate(ctx, questionDef.getTooltipTemplate()),
                addAndRenderTemplate(ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                addAndRenderTemplate(ctx, questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(ctx, questionDef.getStableId()),
                questionCreator.getValidationRules(ctx, questionDef),
                questionDef.getRenderMode(),
                questionDef.isDisplayCalendar(),
                questionDef.getFields(),
                addAndRenderTemplate(ctx, questionDef.getPlaceholderTemplate()),
                questionDef.getPicklistDef().getUseMonthNames(),
                questionDef.getPicklistDef().getStartYear(),
                questionDef.getPicklistDef().getEndYear(),
                questionDef.getPicklistDef().getFirstSelectedYear()
        );
    }

    DateQuestion createDateQuestion(Context ctx, DateQuestionDef questionDef) {
        QuestionCreator questionCreator = ctx.creators().getQuestionCreator();
        return new DateQuestion(
                questionDef.getStableId(),
                addAndRenderTemplate(ctx, questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                addAndRenderTemplate(ctx, questionDef.getTooltipTemplate()),
                addAndRenderTemplate(ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                addAndRenderTemplate(ctx, questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(ctx, questionDef.getStableId()),
                questionCreator.getValidationRules(ctx, questionDef),
                questionDef.getRenderMode(),
                questionDef.isDisplayCalendar(),
                questionDef.getFields(),
                addAndRenderTemplate(ctx, questionDef.getPlaceholderTemplate())
        );
    }

    FileQuestion constructFileQuestion(Context ctx, FileQuestionDef questionDef) {
        QuestionCreator questionCreator = ctx.creators().getQuestionCreator();
        return new FileQuestion(
                questionDef.getStableId(),
                addAndRenderTemplate(ctx, questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                addAndRenderTemplate(ctx, questionDef.getTooltipTemplate()),
                addAndRenderTemplate(ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                addAndRenderTemplate(ctx, questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(ctx, questionDef.getStableId()),
                questionCreator.getValidationRules(ctx, questionDef)
        );
    }

    NumericQuestion createNumericQuestion(Context ctx, NumericQuestionDef questionDef) {
        QuestionCreator questionCreator = ctx.creators().getQuestionCreator();
        return new NumericQuestion(
                questionDef.getStableId(),
                addAndRenderTemplate(ctx, questionDef.getPromptTemplate()),
                addAndRenderTemplate(ctx, questionDef.getPlaceholderTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                addAndRenderTemplate(ctx, questionDef.getTooltipTemplate()),
                addAndRenderTemplate(ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                addAndRenderTemplate(ctx, questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(ctx, questionDef.getStableId()),
                questionCreator.getValidationRules(ctx, questionDef),
                questionDef.getNumericType()
        );
    }

    PicklistQuestion createPicklistQuestion(Context ctx, PicklistQuestionDef questionDef) {
        QuestionCreator questionCreator = ctx.creators().getQuestionCreator();
        return new PicklistQuestion(
                questionDef.getStableId(),
                addAndRenderTemplate(ctx, questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                addAndRenderTemplate(ctx, questionDef.getTooltipTemplate()),
                addAndRenderTemplate(ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                addAndRenderTemplate(ctx, questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(ctx, questionDef.getStableId()),
                questionCreator.getValidationRules(ctx, questionDef),
                questionDef.getSelectMode(),
                questionDef.getRenderMode(),
                addAndRenderTemplate(ctx, questionDef.getPicklistLabelTemplate()),
                CollectionMiscUtil.createListFromAnotherList(questionDef.getAllPicklistOptions(),
                        (picklistOptionDef) -> ctx.creators().getPicklistCreatorHelper().createPicklistOption(ctx, picklistOptionDef)),
                CollectionMiscUtil.createListFromAnotherList(questionDef.getGroups(),
                        (picklistGroupDef) -> ctx.creators().getPicklistCreatorHelper().createPicklistOption(ctx, picklistGroupDef))
        );
    }

    TextQuestion createTextQuestion(Context ctx, TextQuestionDef questionDef) {
        QuestionCreator questionCreator = ctx.creators().getQuestionCreator();
        return new TextQuestion(
                questionDef.getStableId(),
                addAndRenderTemplate(ctx, questionDef.getPromptTemplate()),
                addAndRenderTemplate(ctx, questionDef.getPlaceholderTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                addAndRenderTemplate(ctx, questionDef.getTooltipTemplate()),
                addAndRenderTemplate(ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                addAndRenderTemplate(ctx, questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(ctx, questionDef.getStableId()),
                questionCreator.getValidationRules(ctx, questionDef),
                questionDef.getInputType(),
                questionDef.getSuggestionType(),
                questionDef.getSuggestions(),
                questionDef.isConfirmEntry(),
                addAndRenderTemplate(ctx, questionDef.getConfirmPromptTemplate()),
                addAndRenderTemplate(ctx, questionDef.getMismatchMessageTemplate())
        );
    }
}
