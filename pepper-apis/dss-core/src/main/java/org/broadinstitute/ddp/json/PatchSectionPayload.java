package org.broadinstitute.ddp.json;

import lombok.AllArgsConstructor;
import lombok.Value;

import javax.validation.constraints.PositiveOrZero;

@Value
@AllArgsConstructor
public class PatchSectionPayload {
    @PositiveOrZero
    int index;
}
