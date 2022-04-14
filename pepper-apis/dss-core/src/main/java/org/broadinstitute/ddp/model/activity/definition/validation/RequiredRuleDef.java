package org.broadinstitute.ddp.model.activity.definition.validation;

import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.RuleType;

public final class RequiredRuleDef extends RuleDef {

    public RequiredRuleDef(Template hintTemplate) {
        super(RuleType.REQUIRED, hintTemplate);
    }
}
