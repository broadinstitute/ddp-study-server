package org.broadinstitute.dsm.model.at;

/**
 * Enum for the different statuses a sample can have
 * The corresponding field in the frontend is also called sampleQueue
 */
public enum SampleQueue {

    DEACTIVATED("Deactivated"),
    SENT("Shipped"),
    RECEIVED("Received"),
    QUEUE("Waiting on GP"),
    ERROR("GP manual Label");

    public final String uiText;

    SampleQueue(String status) {
        this.uiText = status;
    }
}
