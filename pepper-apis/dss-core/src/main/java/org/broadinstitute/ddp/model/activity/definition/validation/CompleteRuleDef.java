package org.broadinstitute.ddp.model.activity.definition.validation;

import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.RuleType;

public final class CompleteRuleDef extends RuleDef {

    public CompleteRuleDef(Template hintTemplate) {
        super(RuleType.COMPLETE, hintTemplate);
    }
}
