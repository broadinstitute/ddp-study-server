package org.broadinstitute.ddp.json;

import javax.validation.constraints.PositiveOrZero;

public class PatchSectionPayload {

    @PositiveOrZero
    private int index;

    public PatchSectionPayload(@PositiveOrZero int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }
}
