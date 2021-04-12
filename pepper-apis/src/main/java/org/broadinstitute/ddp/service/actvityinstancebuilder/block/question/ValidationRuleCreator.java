package org.broadinstitute.ddp.service.actvityinstancebuilder.block.question;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.model.activity.definition.validation.AgeRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.CompleteRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.DateFieldRequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.DateRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.IntRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.LengthRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.NumOptionsSelectedRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RegexRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.instance.validation.AgeRangeRule;
import org.broadinstitute.ddp.model.activity.instance.validation.CompleteRule;
import org.broadinstitute.ddp.model.activity.instance.validation.DateFieldRequiredRule;
import org.broadinstitute.ddp.model.activity.instance.validation.DateRangeRule;
import org.broadinstitute.ddp.model.activity.instance.validation.IntRangeRule;
import org.broadinstitute.ddp.model.activity.instance.validation.LengthRule;
import org.broadinstitute.ddp.model.activity.instance.validation.NumOptionsSelectedRule;
import org.broadinstitute.ddp.model.activity.instance.validation.RegexRule;
import org.broadinstitute.ddp.model.activity.instance.validation.RequiredRule;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderContext;

/**
 * Creates {@link Rule}
 */
public class ValidationRuleCreator {

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
            case DATE_RANGE:
                return createDateRangeRule(ctx, (DateRangeRuleDef) ruleDef);
            case DAY_REQUIRED:      // fall through
            case MONTH_REQUIRED:    // fall through
            case YEAR_REQUIRED:
                return createDateFieldRequiredRule(ctx, (DateFieldRequiredRuleDef) ruleDef);
            case NUM_OPTIONS_SELECTED:
                return createNumOptionsSelectedRule(ctx, (NumOptionsSelectedRuleDef) ruleDef);
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

    private String findRuleMessage(AIBuilderContext ctx, RuleDef ruleDef) {
        return ActivityDefStore.getInstance().findValidationRuleMessage(
                ctx.getHandle(),
                ruleDef.getRuleType(),
                ruleDef.getHintTemplateId(),
                ctx.getLangCodeId(),
                ctx.getFormResponse().getCreatedAt());
    }

    private String getHintTitle(AIBuilderContext ctx, RuleDef ruleDef) {
        return ruleDef.getHintTemplate() !=  null ? ruleDef.getHintTemplate().render(ctx.getIsoLangCode()) : null;
    }
}
