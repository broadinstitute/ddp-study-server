package org.broadinstitute.ddp.model.activity.definition.validation;

import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.RuleType;

public final class DateFieldRequiredRuleDef extends RuleDef {

    public DateFieldRequiredRuleDef(RuleType ruleType, Template hintTemplate) {
        super(ruleType, hintTemplate);
        if (!RuleType.YEAR_REQUIRED.equals(ruleType)
                && !RuleType.MONTH_REQUIRED.equals(ruleType)
                && !RuleType.DAY_REQUIRED.equals(ruleType)) {
            throw new IllegalArgumentException("Unknown date field required rule type " + ruleType);
        }
    }
}
