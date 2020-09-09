package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.model.activity.types.DsmNotificationEventType;
import org.broadinstitute.ddp.model.dsm.TestResultEventType;
import org.jdbi.v3.core.Handle;

public class DsmNotificationTrigger extends EventTrigger {

    private DsmNotificationEventType eventType;
    private TestResultEventType testResultEventType;

    public DsmNotificationTrigger(EventConfigurationDto dto) {
        super(dto);
        this.eventType = dto.getDsmNotificationEventType();
        this.testResultEventType = dto.getTestResultEventType();
    }

    public DsmNotificationEventType getDsmEventType() {
        return eventType;
    }

    public TestResultEventType getTestResultEventType() {
        return testResultEventType;
    }

    @Override
    public boolean isTriggered(Handle handle, EventSignal eventSignal) {
        if (eventSignal instanceof DsmNotificationSignal) {
            DsmNotificationSignal dsmSignal = (DsmNotificationSignal) eventSignal;
            if (dsmSignal.getDsmEventType() == DsmNotificationEventType.TEST_RESULT) {
                var expectedType = testResultEventType == null ? TestResultEventType.ANY : testResultEventType;
                return eventType == dsmSignal.getDsmEventType()
                        && dsmSignal.getTestResult() != null
                        && expectedType.matches(dsmSignal.getTestResult().getResult());
            } else {
                return eventType == dsmSignal.getDsmEventType();
            }
        } else {
            return false;
        }
    }
}
