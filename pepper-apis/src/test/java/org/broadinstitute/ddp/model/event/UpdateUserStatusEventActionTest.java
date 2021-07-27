package org.broadinstitute.ddp.model.event;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Test;

public class UpdateUserStatusEventActionTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    public void testDoAction_queued() {
        TransactionWrapper.useTxn(handle -> {
            StudyDto newStudy = TestDataSetupUtil.generateTestStudy(handle, cfg);

            var event = mock(EventConfiguration.class);
            var eventDto = mock(EventConfigurationDto.class);
            var actionSpy = spy(new UpdateUserStatusEventAction(event, eventDto));
            doReturn(1000).when(event).getPostDelaySeconds();
            doReturn(true).when(event).dispatchToHousekeeping();
            doReturn(1L).when(actionSpy).queueDelayedEvent(any(), any());
            doCallRealMethod().when(actionSpy).doAction(any(), any(), any());

            var signal = new EventSignal(testData.getUserId(), testData.getUserId(), testData.getUserGuid(),
                    testData.getUserGuid(), newStudy.getId(), testData.getStudyGuid(), EventTriggerType.USER_STATUS_CHANGED);
            actionSpy.doAction(null, handle, signal);

            verify(actionSpy, never()).doActionSynchronously(any(), any());
            verify(actionSpy, times(1)).queueDelayedEvent(any(), any());

            handle.rollback();
        });
    }

    @Test
    public void testDoAction_synchronously() {
        TransactionWrapper.useTxn(handle -> {
            StudyDto newStudy = TestDataSetupUtil.generateTestStudy(handle, cfg);

            var event = mock(EventConfiguration.class);
            var eventDto = mock(EventConfigurationDto.class);
            doReturn(EnrollmentStatusType.COMPLETED).when(eventDto).getUpdateUserStatusTargetStatusType();

            var actionSpy = spy(new UpdateUserStatusEventAction(event, eventDto));
            doReturn(null).when(event).getPostDelaySeconds();
            doReturn(false).when(event).dispatchToHousekeeping();
            doReturn(1L).when(actionSpy).queueDelayedEvent(any(), any());
            doCallRealMethod().when(actionSpy).doAction(any(), any(), any());

            var signal = new EventSignal(testData.getUserId(), testData.getUserId(), testData.getUserGuid(),
                    testData.getUserGuid(), newStudy.getId(), testData.getStudyGuid(), EventTriggerType.USER_STATUS_CHANGED);
            actionSpy.doAction(null, handle, signal);

            verify(actionSpy, times(1)).doActionSynchronously(any(), any());
            verify(actionSpy, never()).queueDelayedEvent(any(), any());
            verify(actionSpy, times(1)).triggerEvents(any(), argThat(actualSignal -> {
                assertEquals("should invoke downstream events",
                        EventTriggerType.USER_STATUS_CHANGED, actualSignal.getEventTriggerType());
                return true;
            }));
            assertEquals("should have status changed", EnrollmentStatusType.COMPLETED,
                    handle.attach(JdbiUserStudyEnrollment.class)
                            .getEnrollmentStatusByUserAndStudyGuids(testData.getUserGuid(), newStudy.getGuid())
                            .orElse(null));

            handle.rollback();
        });
    }
}
