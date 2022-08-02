package org.broadinstitute.ddp.service.actvityinstancebuilder.form.block.question;

import static org.broadinstitute.ddp.util.QuestionUtil.isReadOnly;

import one.util.streamex.StreamEx;
import org.broadinstitute.ddp.db.dao.QuestionCachedDao;
import org.broadinstitute.ddp.db.dto.EquationQuestionDto;
import org.broadinstitute.ddp.equation.QuestionEvaluator;
import org.broadinstitute.ddp.model.activity.instance.answer.EquationAnswer;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderContext;
import org.broadinstitute.ddp.util.CollectionMiscUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.model.activity.definition.question.AgreementQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.FileQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.NumericQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DecimalQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.EquationQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistGroupDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.ActivityInstanceSelectQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.question.AgreementQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.BoolQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.CompositeQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.DatePicklistQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.DateQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.FileQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.NumericQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.DecimalQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.EquationQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistGroup;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.MatrixGroup;
import org.broadinstitute.ddp.model.activity.instance.question.MatrixOption;
import org.broadinstitute.ddp.model.activity.instance.question.MatrixQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.MatrixRow;
import org.broadinstitute.ddp.model.activity.instance.question.TextQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.ActivityInstanceSelectQuestion;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;

public class QuestionCreatorHelper {

    AgreementQuestion createAgreementQuestion(AIBuilderContext ctx, AgreementQuestionDef questionDef) {
        return new AgreementQuestion(
                questionDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getTooltipTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoFooterTemplate()),
                ctx.getAIBuilderFactory().getQuestionCreator().getAnswers(ctx, questionDef.getStableId()),
                ctx.getAIBuilderFactory().getQuestionCreator().getValidationRules(ctx, questionDef)
        );
    }

    BoolQuestion createBoolQuestion(AIBuilderContext ctx, BoolQuestionDef questionDef) {
        return new BoolQuestion(
                questionDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getTooltipTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoFooterTemplate()),
                ctx.getAIBuilderFactory().getQuestionCreator().getAnswers(ctx, questionDef.getStableId()),
                ctx.getAIBuilderFactory().getQuestionCreator().getValidationRules(ctx, questionDef),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getTrueTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getFalseTemplate()),
                questionDef.getRenderMode()
        );
    }

    CompositeQuestion createCompositeQuestion(AIBuilderContext ctx, CompositeQuestionDef questionDef) {
        QuestionCreator questionCreator = ctx.getAIBuilderFactory().getQuestionCreator();
        return new CompositeQuestion(
                questionDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getTooltipTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getValidationRules(ctx, questionDef),
                questionDef.isAllowMultiple(),
                questionDef.isUnwrapOnExport(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAddButtonTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalItemTemplate()),
                CollectionMiscUtil.createListFromAnotherList(questionDef.getChildren(),
                        (childQuestionDef) -> questionCreator.createQuestion(ctx, childQuestionDef)),
                questionDef.getChildOrientation(), questionDef.getTabularSeparator(),
                questionCreator.getAnswers(ctx, questionDef.getStableId())
        );
    }

    DatePicklistQuestion createDatePickListQuestion(AIBuilderContext ctx, DateQuestionDef questionDef) {
        QuestionCreator questionCreator = ctx.getAIBuilderFactory().getQuestionCreator();
        return new DatePicklistQuestion(
                questionDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getTooltipTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(ctx, questionDef.getStableId()),
                questionCreator.getValidationRules(ctx, questionDef),
                questionDef.getRenderMode(),
                questionDef.isDisplayCalendar(),
                questionDef.getFields(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getPlaceholderTemplate()),
                questionDef.getPicklistDef().getUseMonthNames(),
                questionDef.getPicklistDef().getStartYear(),
                questionDef.getPicklistDef().getEndYear(),
                questionDef.getPicklistDef().getFirstSelectedYear()
        );
    }

    DateQuestion createDateQuestion(AIBuilderContext ctx, DateQuestionDef questionDef) {
        QuestionCreator questionCreator = ctx.getAIBuilderFactory().getQuestionCreator();
        return new DateQuestion(
                questionDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getTooltipTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(ctx, questionDef.getStableId()),
                questionCreator.getValidationRules(ctx, questionDef),
                questionDef.getRenderMode(),
                questionDef.isDisplayCalendar(),
                questionDef.getFields(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getPlaceholderTemplate())
        );
    }

    FileQuestion constructFileQuestion(AIBuilderContext ctx, FileQuestionDef questionDef) {
        QuestionCreator questionCreator = ctx.getAIBuilderFactory().getQuestionCreator();
        return new FileQuestion(
                questionDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getTooltipTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(ctx, questionDef.getStableId()),
                questionCreator.getValidationRules(ctx, questionDef),
                questionDef.getMaxFileSize(),
                questionDef.getMimeTypes()
        );
    }

    NumericQuestion createNumericQuestion(AIBuilderContext ctx, NumericQuestionDef questionDef) {
        QuestionCreator questionCreator = ctx.getAIBuilderFactory().getQuestionCreator();
        return new NumericQuestion(
                questionDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getPromptTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getPlaceholderTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getTooltipTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(ctx, questionDef.getStableId()),
                questionCreator.getValidationRules(ctx, questionDef)
        );
    }

    DecimalQuestion createDecimalQuestion(AIBuilderContext ctx, DecimalQuestionDef questionDef) {
        QuestionCreator questionCreator = ctx.getAIBuilderFactory().getQuestionCreator();
        return new DecimalQuestion(
                questionDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getPromptTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getPlaceholderTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getTooltipTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(ctx, questionDef.getStableId()),
                questionCreator.getValidationRules(ctx, questionDef),
                questionDef.getScale()
        );
    }

    EquationQuestion createEquationQuestion(AIBuilderContext ctx, EquationQuestionDef questionDef) {
        QuestionCreator questionCreator = ctx.getAIBuilderFactory().getQuestionCreator();

        var questionEvaluator = new QuestionEvaluator(ctx.getHandle(), ctx.getInstanceGuid());

        return new EquationQuestion(
                questionDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getPromptTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getPlaceholderTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getTooltipTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoFooterTemplate()),
                StreamEx.of(new QuestionCachedDao(ctx.getHandle()).getJdbiEquationQuestion()
                                .findEquationsByActivityInstanceGuid(ctx.getInstanceGuid()))
                        .filterBy(EquationQuestionDto::getStableId, questionDef.getStableId())
                        .map(questionEvaluator::evaluate)
                        .filter(Objects::nonNull)
                        .map(EquationAnswer::new)
                        .toList(),
                questionCreator.getValidationRules(ctx, questionDef),
                questionDef.getMaximumDecimalPlaces(),
                questionDef.getExpression()
        );
    }

    PicklistQuestion createPicklistQuestion(AIBuilderContext ctx, PicklistQuestionDef questionDef) {
        QuestionCreator questionCreator = ctx.getAIBuilderFactory().getQuestionCreator();

        List<PicklistGroup> picklistGroups = CollectionMiscUtil.createListFromAnotherList(questionDef.getGroups(),
                (picklistGroupDef) -> ctx.getAIBuilderFactory().getPicklistCreatorHelper()
                        .createPicklistGroup(ctx, picklistGroupDef));

        List<PicklistOption> groupPicklistOptions = new ArrayList<>();
        for (PicklistGroupDef picklistGroupDef : questionDef.getGroups()) {
            groupPicklistOptions.addAll(CollectionMiscUtil.createListFromAnotherList(picklistGroupDef.getOptions(),
                    (picklistOptionDef) ->
                            ctx.getAIBuilderFactory().getPicklistCreatorHelper()
                                    .createPicklistOption(ctx, picklistOptionDef, questionDef.getGroups())));
        }

        //localPicklistOptions doesn't include RemotePicklistOptions
        //include option defs of any already selected options (answers)
        List<PicklistAnswer> answers = questionCreator.getAnswers(ctx, questionDef.getStableId());
        List<PicklistOptionDef> options = questionDef.getLocalPicklistOptions();
        if (questionDef.getRenderMode() == PicklistRenderMode.REMOTE_AUTOCOMPLETE && !answers.isEmpty()) {

            List<String> selectedOptsStableIds = answers.stream().map(Answer::getValue)
                    .flatMap(Collection::stream).map(SelectedPicklistOption::getStableId).collect(Collectors.toList());

            options = questionDef.getPicklistOptions().stream()
                    .filter(optionDef -> selectedOptsStableIds.contains(optionDef.getStableId()))
                                    .collect(Collectors.toList());
        }

        List<PicklistOption> picklistOptions = CollectionMiscUtil.createListFromAnotherList(options,
                (picklistOptionDef) ->
                        ctx.getAIBuilderFactory().getPicklistCreatorHelper()
                                .createPicklistOption(ctx, picklistOptionDef, questionDef.getGroups()));

        picklistOptions.addAll(groupPicklistOptions);

        return new PicklistQuestion(
                questionDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getTooltipTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(ctx, questionDef.getStableId()),
                questionCreator.getValidationRules(ctx, questionDef),
                questionDef.getSelectMode(),
                questionDef.getRenderMode(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getPicklistLabelTemplate()),
                picklistOptions,
                picklistGroups
        );
    }

    MatrixQuestion createMatrixQuestion(AIBuilderContext ctx, MatrixQuestionDef questionDef) {
        QuestionCreator questionCreator = ctx.getAIBuilderFactory().getQuestionCreator();

        List<MatrixGroup> matrixGroups = CollectionMiscUtil.createListFromAnotherList(questionDef.getGroups(),
                (matrixGroupDef) -> ctx.getAIBuilderFactory().getMatrixCreatorHelper().createMatrixGroup(ctx, matrixGroupDef));

        List<MatrixOption> matrixOptions = CollectionMiscUtil.createListFromAnotherList(questionDef.getOptions(),
                (matrixOptionDef) -> ctx.getAIBuilderFactory().getMatrixCreatorHelper().createMatrixOption(ctx, matrixOptionDef));

        List<MatrixRow> matrixQuestionsRows = CollectionMiscUtil.createListFromAnotherList(questionDef.getRows(),
                (matrixQuestionDef) -> ctx.getAIBuilderFactory().getMatrixCreatorHelper().createMatrixQuestionRow(ctx, matrixQuestionDef));

        return new MatrixQuestion(
                questionDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isRenderModal(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getTooltipTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoFooterTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getModalTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getModalTitleTemplate()),
                questionCreator.getAnswers(ctx, questionDef.getStableId()),
                questionCreator.getValidationRules(ctx, questionDef),
                questionDef.getSelectMode(),
                matrixGroups,
                matrixOptions,
                matrixQuestionsRows
        );
    }

    TextQuestion createTextQuestion(AIBuilderContext ctx, TextQuestionDef questionDef) {
        QuestionCreator questionCreator = ctx.getAIBuilderFactory().getQuestionCreator();
        return new TextQuestion(
                questionDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getPromptTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getPlaceholderTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getConfirmPlaceholderTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getTooltipTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(ctx, questionDef.getStableId()),
                questionCreator.getValidationRules(ctx, questionDef),
                questionDef.getInputType(),
                questionDef.getSuggestionType(),
                questionDef.getSuggestions(),
                questionDef.isConfirmEntry(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getConfirmPromptTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getMismatchMessageTemplate())
        );
    }

    ActivityInstanceSelectQuestion createActivityInstanceSelectQuestion(AIBuilderContext ctx,
                                                                        ActivityInstanceSelectQuestionDef questionDef) {
        QuestionCreator questionCreator = ctx.getAIBuilderFactory().getQuestionCreator();
        return new ActivityInstanceSelectQuestion(
                questionDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(questionDef, ctx.getFormResponse().getLatestStatus().getType(), ctx.getPreviousInstanceId()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getTooltipTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoHeaderTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(ctx, questionDef.getStableId()),
                questionCreator.getValidationRules(ctx, questionDef),
                questionDef.getActivityCodes());
    }
}
