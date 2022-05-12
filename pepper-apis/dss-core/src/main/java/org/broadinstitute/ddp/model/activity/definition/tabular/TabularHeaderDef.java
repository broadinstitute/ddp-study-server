package org.broadinstitute.ddp.model.activity.definition.tabular;

import lombok.Value;
import org.broadinstitute.ddp.model.activity.definition.template.Template;

@Value
public class TabularHeaderDef {
    int startColumn;
    int endColumn;
    Template label;
}
