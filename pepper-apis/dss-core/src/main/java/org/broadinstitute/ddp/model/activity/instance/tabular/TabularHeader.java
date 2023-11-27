package org.broadinstitute.ddp.model.activity.instance.tabular;

import lombok.Data;

@Data
public final class TabularHeader {
    private final int columnSpan;
    private final Long labelTemplateId;
    private String label;
}
