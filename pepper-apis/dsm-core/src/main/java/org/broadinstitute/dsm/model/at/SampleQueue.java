package org.broadinstitute.dsm.model.at;

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
