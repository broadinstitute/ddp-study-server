package org.broadinstitute.ddp.model.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
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

public class EnrollUserEventActionTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    public void testDoAction() {
        TransactionWrapper.useTxn(handle -> {
            StudyDto newStudy = TestDataSetupUtil.generateTestStudy(handle, cfg);

            var event = mock(EventConfiguration.class);
            var eventDto = mock(EventConfigurationDto.class);
            var actionSpy = spy(new EnrollUserEventAction(event, eventDto));
            doNothing().when(actionSpy).triggerEvents(any(), any());
            doCallRealMethod().when(actionSpy).doAction(any(), any(), any());

            var signal = new EventSignal(testData.getUserId(), testData.getUserId(), testData.getUserGuid(),
                    testData.getUserGuid(), newStudy.getId(), EventTriggerType.USER_REGISTERED);
            actionSpy.doAction(null, handle, signal);

            assertTrue("should be enrolled", handle.attach(JdbiUserStudyEnrollment.class)
                    .findFirstStatusMillis(newStudy.getId(), testData.getUserId(), EnrollmentStatusType.ENROLLED)
                    .isPresent());
            verify(actionSpy, times(1)).triggerEvents(any(), argThat(actualSignal -> {
                assertEquals("should invoke downstream events",
                        EventTriggerType.USER_STATUS_CHANGE, actualSignal.getEventTriggerType());
                return true;
            }));

            handle.rollback();
        });
    }
}
