package org.broadinstitute.ddp.model.event;

import javax.annotation.Nullable;

import org.broadinstitute.ddp.model.activity.types.DsmNotificationEventType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.dsm.TestResult;

public class DsmNotificationSignal extends EventSignal {

    private DsmNotificationEventType eventType;
    private TestResult testResult;

    public DsmNotificationSignal(long operatorId, long participantId, String participantGuid,
                                 long studyId, DsmNotificationEventType eventType,
                                 @Nullable TestResult testResult) {
        super(operatorId, participantId, participantGuid, studyId, EventTriggerType.DSM_NOTIFICATION);
        this.eventType = eventType;
        this.testResult = testResult;
    }

    public DsmNotificationSignal(long operatorId, long participantId, String participantGuid,
                                 long studyId, DsmNotificationEventType eventType) {
        this(operatorId, participantId, participantGuid, studyId, eventType, null);
    }

    public DsmNotificationEventType getDsmEventType() {
        return eventType;
    }

    public TestResult getTestResult() {
        return testResult;
    }
}
