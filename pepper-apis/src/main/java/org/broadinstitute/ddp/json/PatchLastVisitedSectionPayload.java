package org.broadinstitute.ddp.json;

import javax.validation.constraints.PositiveOrZero;

public class PatchLastVisitedSectionPayload {

    @PositiveOrZero
    private int lastVisitedSection;

    public PatchLastVisitedSectionPayload(@PositiveOrZero int lastVisitedSection) {
        this.lastVisitedSection = lastVisitedSection;
    }

    public int getLastVisitedSection() {
        return lastVisitedSection;
    }
}
