package org.broadinstitute.ddp.model.activity.definition.validation;

import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.RuleType;

public final class UniqueValueRuleDef extends RuleDef {

    public UniqueValueRuleDef(Template hintTemplate) {
        super(RuleType.UNIQUE_VALUE, hintTemplate);
    }
}
