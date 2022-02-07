package org.broadinstitute.ddp.model.dsm;

/**
 * The reason of a kit. What each reason means and how it affects workflow will depend on the study's configuration.
 */
public enum KitReasonType {
    /**
     * Should represent a normal kit that's part of the regular workflow.
     */
    NORMAL,
    /**
     * Should represent a replacement kit for a normal kit. Useful for replacing a lost kit.
     */
    REPLACEMENT,
    /**
     * Should represent a kit only and no event triggering. Useful for replacing a damaged-on-arrival kit, or for
     * sending an additional kit.
     */
    STANDALONE,
    /**
     * Should represent a kit that invokes a kit workflow that's out-of-band, in an adhoc manner.
     */
    ADHOC,
}
