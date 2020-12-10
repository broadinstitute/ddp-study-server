package org.broadinstitute.ddp.model.event;

import javax.annotation.Nullable;

import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.dsm.DsmNotificationEventType;
import org.broadinstitute.ddp.model.dsm.KitReasonType;
import org.broadinstitute.ddp.model.dsm.TestResult;

public class DsmNotificationSignal extends EventSignal {

    private DsmNotificationEventType eventType;
    private String kitRequestId;
    private KitReasonType kitReasonType;
    private TestResult testResult;

    public DsmNotificationSignal(long operatorId, long participantId, String participantGuid,
                                 String operatorGuid, long studyId, DsmNotificationEventType eventType,
                                 @Nullable String kitRequestId, KitReasonType kitReasonType,
                                 @Nullable TestResult testResult) {
        super(operatorId, participantId, participantGuid, operatorGuid, studyId, EventTriggerType.DSM_NOTIFICATION);
        this.eventType = eventType;
        this.kitRequestId = kitRequestId;
        this.kitReasonType = kitReasonType;
        this.testResult = testResult;
    }

    public DsmNotificationEventType getDsmEventType() {
        return eventType;
    }

    public String getKitRequestId() {
        return kitRequestId;
    }

    public KitReasonType getKitReasonType() {
        return kitReasonType;
    }

    public TestResult getTestResult() {
        return testResult;
    }
}
