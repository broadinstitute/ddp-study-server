package org.broadinstitute.dsm.model;

import lombok.NonNull;
import org.broadinstitute.dsm.model.birch.DSMTestResult;
import org.broadinstitute.dsm.util.KitEvent;

public class TestResultEvent extends KitEvent {
    DSMTestResult eventData;

    public TestResultEvent(String eventInfo, @NonNull String eventType, @NonNull Long eventDate,  String kitUploadType, String ddpKitRequestId, DSMTestResult result) {
        super(eventInfo, eventType, eventDate, kitUploadType, ddpKitRequestId);
        this.eventData = result;
    }
}
