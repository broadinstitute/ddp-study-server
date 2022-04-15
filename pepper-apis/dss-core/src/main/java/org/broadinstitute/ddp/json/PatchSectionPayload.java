package org.broadinstitute.ddp.json;

import javax.validation.constraints.PositiveOrZero;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class PatchSectionPayload {
    @PositiveOrZero
    int index;
}
