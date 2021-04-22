package org.broadinstitute.ddp.service.actvityinstancebuilder.form.block.question;

import static org.broadinstitute.ddp.util.QuestionUtil.isReadOnly;

import java.util.List;

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
import org.broadinstitute.ddp.model.activity.instance.question.PicklistGroup;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.TextQuestion;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderContext;
import org.broadinstitute.ddp.util.CollectionMiscUtil;

public class QuestionCreatorHelper {

    AgreementQuestion createAgreementQuestion(AIBuilderContext ctx, AgreementQuestionDef questionDef) {
        return new AgreementQuestion(
                questionDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getTooltipTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getAdditionalInfoFooterTemplate()),
                ctx.getAIBuilderFactory().getAICreatorsFactory().getQuestionCreator().getAnswers(ctx, questionDef.getStableId()),
                ctx.getAIBuilderFactory().getAICreatorsFactory().getQuestionCreator().getValidationRules(ctx, questionDef)
        );
    }

    BoolQuestion createBoolQuestion(AIBuilderContext ctx, BoolQuestionDef questionDef) {
        return new BoolQuestion(
                questionDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getTooltipTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getAdditionalInfoFooterTemplate()),
                ctx.getAIBuilderFactory().getAICreatorsFactory().getQuestionCreator().getAnswers(ctx, questionDef.getStableId()),
                ctx.getAIBuilderFactory().getAICreatorsFactory().getQuestionCreator().getValidationRules(ctx, questionDef),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getTrueTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getFalseTemplate())
        );
    }

    CompositeQuestion createCompositeQuestion(AIBuilderContext ctx, CompositeQuestionDef questionDef) {
        QuestionCreator questionCreator = ctx.getAIBuilderFactory().getAICreatorsFactory().getQuestionCreator();
        return new CompositeQuestion(
                questionDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getTooltipTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getValidationRules(ctx, questionDef),
                questionDef.isAllowMultiple(),
                questionDef.isUnwrapOnExport(),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getAddButtonTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getAdditionalItemTemplate()),
                CollectionMiscUtil.createListFromAnotherList(questionDef.getChildren(),
                        (childQuestionDef) -> questionCreator.createQuestion(ctx, childQuestionDef)),
                questionDef.getChildOrientation(),
                questionCreator.getAnswers(ctx, questionDef.getStableId())
        );
    }

    DatePicklistQuestion createDatePickListQuestion(AIBuilderContext ctx, DateQuestionDef questionDef) {
        QuestionCreator questionCreator = ctx.getAIBuilderFactory().getAICreatorsFactory().getQuestionCreator();
        return new DatePicklistQuestion(
                questionDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getTooltipTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(ctx, questionDef.getStableId()),
                questionCreator.getValidationRules(ctx, questionDef),
                questionDef.getRenderMode(),
                questionDef.isDisplayCalendar(),
                questionDef.getFields(),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getPlaceholderTemplate()),
                questionDef.getPicklistDef().getUseMonthNames(),
                questionDef.getPicklistDef().getStartYear(),
                questionDef.getPicklistDef().getEndYear(),
                questionDef.getPicklistDef().getFirstSelectedYear()
        );
    }

    DateQuestion createDateQuestion(AIBuilderContext ctx, DateQuestionDef questionDef) {
        QuestionCreator questionCreator = ctx.getAIBuilderFactory().getAICreatorsFactory().getQuestionCreator();
        return new DateQuestion(
                questionDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getTooltipTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(ctx, questionDef.getStableId()),
                questionCreator.getValidationRules(ctx, questionDef),
                questionDef.getRenderMode(),
                questionDef.isDisplayCalendar(),
                questionDef.getFields(),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getPlaceholderTemplate())
        );
    }

    FileQuestion constructFileQuestion(AIBuilderContext ctx, FileQuestionDef questionDef) {
        QuestionCreator questionCreator = ctx.getAIBuilderFactory().getAICreatorsFactory().getQuestionCreator();
        return new FileQuestion(
                questionDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getTooltipTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(ctx, questionDef.getStableId()),
                questionCreator.getValidationRules(ctx, questionDef)
        );
    }

    NumericQuestion createNumericQuestion(AIBuilderContext ctx, NumericQuestionDef questionDef) {
        QuestionCreator questionCreator = ctx.getAIBuilderFactory().getAICreatorsFactory().getQuestionCreator();
        return new NumericQuestion(
                questionDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getPromptTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getPlaceholderTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getTooltipTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(ctx, questionDef.getStableId()),
                questionCreator.getValidationRules(ctx, questionDef),
                questionDef.getNumericType()
        );
    }

    PicklistQuestion createPicklistQuestion(AIBuilderContext ctx, PicklistQuestionDef questionDef) {
        QuestionCreator questionCreator = ctx.getAIBuilderFactory().getAICreatorsFactory().getQuestionCreator();

        List<PicklistGroup> picklistGroups = CollectionMiscUtil.createListFromAnotherList(questionDef.getGroups(),
                (picklistGroupDef) -> ctx.getAIBuilderFactory().getAICreatorsFactory().getPicklistCreatorHelper()
                        .createPicklistGroup(ctx, picklistGroupDef));

        List<PicklistOption> picklistOptions = CollectionMiscUtil.createListFromAnotherList(questionDef.getAllPicklistOptions(),
                (picklistOptionDef) ->
                        ctx.getAIBuilderFactory().getAICreatorsFactory().getPicklistCreatorHelper()
                                .createPicklistOption(ctx, picklistOptionDef, questionDef.getGroups()));

        return new PicklistQuestion(
                questionDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getTooltipTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(ctx, questionDef.getStableId()),
                questionCreator.getValidationRules(ctx, questionDef),
                questionDef.getSelectMode(),
                questionDef.getRenderMode(),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getPicklistLabelTemplate()),
                picklistOptions,
                picklistGroups
        );
    }

    TextQuestion createTextQuestion(AIBuilderContext ctx, TextQuestionDef questionDef) {
        QuestionCreator questionCreator = ctx.getAIBuilderFactory().getAICreatorsFactory().getQuestionCreator();
        return new TextQuestion(
                questionDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getPromptTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getPlaceholderTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getTooltipTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(ctx, questionDef.getStableId()),
                questionCreator.getValidationRules(ctx, questionDef),
                questionDef.getInputType(),
                questionDef.getSuggestionType(),
                questionDef.getSuggestions(),
                questionDef.isConfirmEntry(),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getConfirmPromptTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, questionDef.getMismatchMessageTemplate())
        );
    }
}
