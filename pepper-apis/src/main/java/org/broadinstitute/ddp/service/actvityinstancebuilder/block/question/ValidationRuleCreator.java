package org.broadinstitute.ddp.service.actvityinstancebuilder.block.question;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.broadinstitute.ddp.db.dao.ValidationDao;
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
import org.broadinstitute.ddp.service.actvityinstancebuilder.AbstractCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;

/**
 * Creates {@link Rule}
 */
public class ValidationRuleCreator extends AbstractCreator {

    public ValidationRuleCreator(ActivityInstanceFromDefinitionBuilder.Context context) {
        super(context);
    }

    public Rule createRule(RuleDef ruleDef) {
        switch (ruleDef.getRuleType()) {
            case REGEX:
                return createRegExpRule((RegexRuleDef) ruleDef);
            case LENGTH:
                return createLengthRule((LengthRuleDef) ruleDef);
            case COMPLETE:
                return createCompleteRule((CompleteRuleDef) ruleDef);
            case REQUIRED:
                return createRequiredRule((RequiredRuleDef) ruleDef);
            case AGE_RANGE:
                return createAgeRangeRule((AgeRangeRuleDef) ruleDef);
            case INT_RANGE:
                return createIntRangeRule((IntRangeRuleDef) ruleDef);
            case DATE_RANGE:
                return createDateRangeRule((DateRangeRuleDef) ruleDef);
            case DAY_REQUIRED:      // fall through
            case MONTH_REQUIRED:    // fall through
            case YEAR_REQUIRED:
                return createDateFieldRequiredRule((DateFieldRequiredRuleDef) ruleDef);
            case NUM_OPTIONS_SELECTED:
                return createNumOptionsSelectedRule((NumOptionsSelectedRuleDef) ruleDef);
            default:
                throw new IllegalStateException("Unexpected value: " + ruleDef.getRuleType());
        }
    }

    private RegexRule createRegExpRule(RegexRuleDef ruleDef) {
        return RegexRule.of(
                ruleDef.getRuleId(),
                resolveRuleMessage(ruleDef),
                getHintTitle(ruleDef),
                ruleDef.getAllowSave(),
                ruleDef.getPattern()
        );
    }

    private LengthRule createLengthRule(LengthRuleDef ruleDef) {
        return LengthRule.of(
                ruleDef.getRuleId(),
                resolveRuleMessage(ruleDef),
                getHintTitle(ruleDef),
                ruleDef.getAllowSave(),
                ruleDef.getMin(),
                ruleDef.getMax()
        );
    }

    private CompleteRule createCompleteRule(CompleteRuleDef ruleDef) {
        return new CompleteRule(
                ruleDef.getRuleId(),
                resolveRuleMessage(ruleDef),
                getHintTitle(ruleDef),
                ruleDef.getAllowSave()
        );
    }

    private RequiredRule createRequiredRule(RequiredRuleDef ruleDef) {
        return new RequiredRule(
                ruleDef.getRuleId(),
                resolveRuleMessage(ruleDef),
                getHintTitle(ruleDef),
                ruleDef.getAllowSave()
        );
    }

    private AgeRangeRule createAgeRangeRule(AgeRangeRuleDef ruleDef) {
        return AgeRangeRule.of(
                ruleDef.getRuleId(),
                resolveRuleMessage(ruleDef),
                getHintTitle(ruleDef),
                ruleDef.getAllowSave(),
                ruleDef.getMinAge(),
                ruleDef.getMaxAge()
        );
    }

    private IntRangeRule createIntRangeRule(IntRangeRuleDef ruleDef) {
        return IntRangeRule.of(
                ruleDef.getRuleId(),
                resolveRuleMessage(ruleDef),
                getHintTitle(ruleDef),
                ruleDef.getAllowSave(),
                ruleDef.getMin(),
                ruleDef.getMax()
        );
    }

    private DateRangeRule createDateRangeRule(DateRangeRuleDef ruleDef) {
        return DateRangeRule.of(
                ruleDef.getRuleId(),
                resolveRuleMessage(ruleDef),
                getHintTitle(ruleDef),
                ruleDef.getAllowSave(),
                ruleDef.getStartDate(),
                ruleDef.isUseTodayAsEnd() ? LocalDate.now(ZoneOffset.UTC) : ruleDef.getEndDate()
        );
    }

    private DateFieldRequiredRule createDateFieldRequiredRule(DateFieldRequiredRuleDef ruleDef) {
        return DateFieldRequiredRule.of(
                ruleDef.getRuleType(),
                ruleDef.getRuleId(),
                resolveRuleMessage(ruleDef),
                getHintTitle(ruleDef),
                ruleDef.getAllowSave()
        );
    }

    private NumOptionsSelectedRule createNumOptionsSelectedRule(NumOptionsSelectedRuleDef ruleDef) {
        return NumOptionsSelectedRule.of(
                ruleDef.getRuleId(),
                resolveRuleMessage(ruleDef),
                getHintTitle(ruleDef),
                ruleDef.getAllowSave(),
                ruleDef.getMin(),
                ruleDef.getMax()
        );
    }

    private String resolveRuleMessage(RuleDef ruleDef) {
        var validationDao = context.getHandle().attach(ValidationDao.class);
        return validationDao.getJdbiI18nValidationMsgTrans().getValidationMessage(
                validationDao.getJdbiValidationType().getTypeId(ruleDef.getRuleType()),
                context.getLangCodeId()
        );
    }

    private String getHintTitle(RuleDef ruleDef) {
        return ruleDef.getHintTemplate() !=  null ? ruleDef.getHintTemplate().render(context.getIsoLangCode()) : null;
    }
}
