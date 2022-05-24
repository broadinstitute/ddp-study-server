package org.broadinstitute.ddp.model.activity.instance.tabular;

import lombok.Value;

@Value
public class TabularHeader {
    int columnSpan;
    Long labelTemplateId;
}
