package org.broadinstitute.ddp.service.actvityinstancebuilder.form.block.question;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dao.ValidationDao;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.model.activity.definition.types.DecimalDef;
import org.broadinstitute.ddp.model.activity.definition.validation.AgeRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.CompleteRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.DateFieldRequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.DateRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.IntRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.DecimalRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.LengthRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.NumOptionsSelectedRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RegexRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.ComparisonRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.UniqueRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.UniqueValueRuleDef;
import org.broadinstitute.ddp.model.activity.instance.validation.AgeRangeRule;
import org.broadinstitute.ddp.model.activity.instance.validation.CompleteRule;
import org.broadinstitute.ddp.model.activity.instance.validation.DateFieldRequiredRule;
import org.broadinstitute.ddp.model.activity.instance.validation.DateRangeRule;
import org.broadinstitute.ddp.model.activity.instance.validation.IntRangeRule;
import org.broadinstitute.ddp.model.activity.instance.validation.DecimalRangeRule;
import org.broadinstitute.ddp.model.activity.instance.validation.ComparisonRule;
import org.broadinstitute.ddp.model.activity.instance.validation.LengthRule;
import org.broadinstitute.ddp.model.activity.instance.validation.NumOptionsSelectedRule;
import org.broadinstitute.ddp.model.activity.instance.validation.RegexRule;
import org.broadinstitute.ddp.model.activity.instance.validation.RequiredRule;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.instance.validation.UniqueRule;
import org.broadinstitute.ddp.model.activity.instance.validation.UniqueValueRule;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderContext;
import org.jdbi.v3.core.Handle;

/**
 * Creates {@link Rule}
 */
public class ValidationRuleCreator {

    private static final I18nContentRenderer i18nContentRenderer = new I18nContentRenderer();

    public Rule createRule(AIBuilderContext ctx, RuleDef ruleDef) {
        switch (ruleDef.getRuleType()) {
            case REGEX:
                return createRegExpRule(ctx, (RegexRuleDef) ruleDef);
            case LENGTH:
                return createLengthRule(ctx, (LengthRuleDef) ruleDef);
            case COMPLETE:
                return createCompleteRule(ctx, (CompleteRuleDef) ruleDef);
            case REQUIRED:
                return createRequiredRule(ctx, (RequiredRuleDef) ruleDef);
            case AGE_RANGE:
                return createAgeRangeRule(ctx, (AgeRangeRuleDef) ruleDef);
            case INT_RANGE:
                return createIntRangeRule(ctx, (IntRangeRuleDef) ruleDef);
            case DECIMAL_RANGE:
                return createDecimalRangeRule(ctx, (DecimalRangeRuleDef) ruleDef);
            case DATE_RANGE:
                return createDateRangeRule(ctx, (DateRangeRuleDef) ruleDef);
            case DAY_REQUIRED:      // fall through
            case MONTH_REQUIRED:    // fall through
            case YEAR_REQUIRED:
                return createDateFieldRequiredRule(ctx, (DateFieldRequiredRuleDef) ruleDef);
            case NUM_OPTIONS_SELECTED:
                return createNumOptionsSelectedRule(ctx, (NumOptionsSelectedRuleDef) ruleDef);
            case UNIQUE:
                return createUniqueRule(ctx, (UniqueRuleDef) ruleDef);
            case UNIQUE_VALUE:
                return createUniqueValueRule(ctx, (UniqueValueRuleDef) ruleDef);
            case COMPARISON:
                return createComparisonRule(ctx, (ComparisonRuleDef) ruleDef);
            default:
                throw new IllegalStateException("Unexpected value: " + ruleDef.getRuleType());
        }
    }

    private RegexRule createRegExpRule(AIBuilderContext ctx, RegexRuleDef ruleDef) {
        return RegexRule.of(
                ruleDef.getRuleId(),
                findRuleMessage(ctx, ruleDef),
                getHintTitle(ctx, ruleDef),
                ruleDef.getAllowSave(),
                ruleDef.getPattern()
        );
    }

    private LengthRule createLengthRule(AIBuilderContext ctx, LengthRuleDef ruleDef) {
        return LengthRule.of(
                ruleDef.getRuleId(),
                findRuleMessage(ctx, ruleDef),
                getHintTitle(ctx, ruleDef),
                ruleDef.getAllowSave(),
                ruleDef.getMin(),
                ruleDef.getMax()
        );
    }

    private CompleteRule createCompleteRule(AIBuilderContext ctx, CompleteRuleDef ruleDef) {
        return new CompleteRule(
                ruleDef.getRuleId(),
                findRuleMessage(ctx, ruleDef),
                getHintTitle(ctx, ruleDef),
                ruleDef.getAllowSave()
        );
    }

    private RequiredRule createRequiredRule(AIBuilderContext ctx, RequiredRuleDef ruleDef) {
        return new RequiredRule(
                ruleDef.getRuleId(),
                getHintTitle(ctx, ruleDef),
                findRuleMessage(ctx, ruleDef),
                ruleDef.getAllowSave()
        );
    }

    private AgeRangeRule createAgeRangeRule(AIBuilderContext ctx, AgeRangeRuleDef ruleDef) {
        return AgeRangeRule.of(
                ruleDef.getRuleId(),
                findRuleMessage(ctx, ruleDef),
                getHintTitle(ctx, ruleDef),
                ruleDef.getAllowSave(),
                ruleDef.getMinAge(),
                ruleDef.getMaxAge()
        );
    }

    private IntRangeRule createIntRangeRule(AIBuilderContext ctx, IntRangeRuleDef ruleDef) {
        return IntRangeRule.of(
                ruleDef.getRuleId(),
                findRuleMessage(ctx, ruleDef),
                getHintTitle(ctx, ruleDef),
                ruleDef.getAllowSave(),
                ruleDef.getMin(),
                ruleDef.getMax()
        );
    }

    private DecimalRangeRule createDecimalRangeRule(AIBuilderContext ctx, DecimalRangeRuleDef ruleDef) {
        return DecimalRangeRule.of(
                ruleDef.getRuleId(),
                findRuleMessage(ctx, ruleDef),
                getHintTitle(ctx, ruleDef),
                ruleDef.getAllowSave(),
                Optional.ofNullable(ruleDef.getMin()).map(DecimalDef::toBigDecimal).orElse(null),
                Optional.ofNullable(ruleDef.getMax()).map(DecimalDef::toBigDecimal).orElse(null)
        );
    }

    private DateRangeRule createDateRangeRule(AIBuilderContext ctx, DateRangeRuleDef ruleDef) {
        return DateRangeRule.of(
                ruleDef.getRuleId(),
                findRuleMessage(ctx, ruleDef),
                getHintTitle(ctx, ruleDef),
                ruleDef.getAllowSave(),
                ruleDef.getStartDate(),
                ruleDef.isUseTodayAsEnd() ? LocalDate.now(ZoneOffset.UTC) : ruleDef.getEndDate()
        );
    }

    private DateFieldRequiredRule createDateFieldRequiredRule(AIBuilderContext ctx, DateFieldRequiredRuleDef ruleDef) {
        return DateFieldRequiredRule.of(
                ruleDef.getRuleType(),
                ruleDef.getRuleId(),
                findRuleMessage(ctx, ruleDef),
                getHintTitle(ctx, ruleDef),
                ruleDef.getAllowSave()
        );
    }

    private NumOptionsSelectedRule createNumOptionsSelectedRule(AIBuilderContext ctx, NumOptionsSelectedRuleDef ruleDef) {
        return NumOptionsSelectedRule.of(
                ruleDef.getRuleId(),
                findRuleMessage(ctx, ruleDef),
                getHintTitle(ctx, ruleDef),
                ruleDef.getAllowSave(),
                ruleDef.getMin(),
                ruleDef.getMax()
        );
    }

    private UniqueRule createUniqueRule(AIBuilderContext ctx, UniqueRuleDef ruleDef) {
        return UniqueRule.of(
                ruleDef.getRuleId(),
                findRuleMessage(ctx, ruleDef),
                getHintTitle(ctx, ruleDef),
                ruleDef.getAllowSave()
        );
    }

    private UniqueValueRule createUniqueValueRule(AIBuilderContext ctx, UniqueValueRuleDef ruleDef) {
        return UniqueValueRule.of(
                ruleDef.getRuleId(),
                findRuleMessage(ctx, ruleDef),
                getHintTitle(ctx, ruleDef),
                ruleDef.getAllowSave()
        );
    }

    private ComparisonRule createComparisonRule(AIBuilderContext ctx, ComparisonRuleDef ruleDef) {
        QuestionDto questionDto = TransactionWrapper.withTxn(handle -> handle.attach(QuestionDao.class).getJdbiQuestion()
                .findLatestDtoByStudyGuidAndQuestionStableId(ctx.getStudyGuid(), ruleDef.getValueStableId())
                .orElseThrow(() -> new RuntimeException(
                        String.format("Can't find question by stable ID: %s & Study GUID: %s",
                                ruleDef.getValueStableId(), ctx.getStudyGuid()))));

        return ComparisonRule.builder()
                .id(ruleDef.getRuleId())
                .type(ruleDef.getRuleType())
                .message(findRuleMessage(ctx, ruleDef))
                .correctionHint(getHintTitle(ctx, ruleDef))
                .allowSave(ruleDef.getAllowSave())
                .comparisonType(ruleDef.getComparison())
                .referenceQuestionStableId(ruleDef.getValueStableId())
                .referenceQuestionId(questionDto.getId())
                .build();
    }

    private String findRuleMessage(AIBuilderContext ctx, RuleDef ruleDef) {
        return ActivityDefStore.getInstance().findValidationRuleMessage(
                ctx.getHandle(),
                ruleDef.getRuleType(),
                ruleDef.getHintTemplateId(),
                ctx.getLanguageDto().getId(),
                ctx.getFormResponse().getCreatedAt(),
                ctx.getAIBuilderFactory().getValidationRuleCreator()::detectValidationRuleMessage);
    }

    private String getHintTitle(AIBuilderContext ctx, RuleDef ruleDef) {
        return ruleDef.getHintTemplate() !=  null
                ? ruleDef.getHintTemplate().render(ctx.getIsoLangCode(), ctx.getRendererInitialContext())
                : null;
    }

    public String detectValidationRuleMessage(
            Handle handle, RuleType ruleType, Long hintTemplateId, long langCodeId, long timestamp) {
        String correctionHint = null;
        if (hintTemplateId != null) {
            correctionHint = i18nContentRenderer.renderContent(handle, hintTemplateId, langCodeId, timestamp);
        }
        if (correctionHint != null) {
            return correctionHint;
        } else {
            var validationDao = handle.attach(ValidationDao.class);
            return validationDao.getJdbiI18nValidationMsgTrans().getValidationMessage(
                    validationDao.getJdbiValidationType().getTypeId(ruleType), langCodeId);
        }
    }

    @FunctionalInterface
    public interface ValidationRuleMessageDetector {

        String detectValidationRuleMessage(
                Handle handle, RuleType ruleType, Long hintTemplateId, long langCodeId, long timestamp);
    }
}
