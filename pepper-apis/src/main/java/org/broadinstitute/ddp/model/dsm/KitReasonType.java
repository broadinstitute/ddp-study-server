package org.broadinstitute.ddp.model.dsm;

public enum KitReasonType {
    /**
     * A normal kit that's part of the regular workflow.
     */
    NORMAL,
    /**
     * A replacement kit for a normal kit, which may have been lost, damaged, etc.
     * Or could be a new additional kit. Studies may decide what these mean by
     * leveraging it in their configurations.
     */
    REPLACEMENT,
}
