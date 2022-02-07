package org.broadinstitute.ddp.model.activity.definition.validation;

import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.RuleType;

public final class UniqueRuleDef extends RuleDef {

    public UniqueRuleDef(Template hintTemplate) {
        super(RuleType.UNIQUE, hintTemplate);
    }
}
