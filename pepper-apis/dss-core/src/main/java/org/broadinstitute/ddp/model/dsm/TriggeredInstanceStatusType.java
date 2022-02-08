package org.broadinstitute.ddp.model.dsm;

import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;

/**
 * Status used for activity instances where the
 * creation is triggered by DSM.
 *
 * <p>This is only used when interacting with DSM. It is not used
 * for any internal Pepper flows.
 *
 * @see TriggeredInstance
 * @see GetDsmTriggeredInstancesRoute
 */
public enum TriggeredInstanceStatusType {
    STARTED,
    COMPLETE;

    /**
     * Converts a Pepper instance status type to a value which is consumable by DSM.
     *
     * <p>Since DSM does not recognize the difference between an activity being newly
     * created, and one being partially completed by a participant, the Pepper CREATED and
     * IN_PROGRESS status is mapped to DSM's STARTED status.
     *
     * @param status the Pepper activity instance status type to convert
     * @return the associated DSM triggered instance status
     * @throws IllegalArgumentException if the Pepper activity instance status does not have a valid mapping
     * @see InstanceStatusType
     */
    public static final TriggeredInstanceStatusType valueOf(InstanceStatusType status) {
        switch (status) {
            case CREATED:
                return STARTED;
            case IN_PROGRESS:
                return STARTED;
            case COMPLETE:
                return COMPLETE;
            default:
                throw new IllegalArgumentException("unrecognized status '" + status.toString() + "'");
        }
    }
}
