package org.broadinstitute.ddp.model.event;

import static org.junit.Assert.assertEquals;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.event.publish.TaskPublisher;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests handler {@link UpdateCustomWorkflowEventAction} of
 * event {@link EventActionType#UPDATE_CUSTOM_WORKFLOW}
 */
public class UpdateCustomWorkflowEventActionTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    private static String expectedTaskName;
    private static String expectedEventPayload;
    private static String expectedStudyGuid;
    private static String expectedParticipantGuid;


    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    public void testDoAction() {
        TransactionWrapper.useTxn(handle -> {
            var signal = new EventSignal(
                    testData.getUserId(), testData.getUserId(), testData.getUserGuid(), testData.getUserGuid(),
                    testData.getStudyId(), testData.getStudyGuid(), EventTriggerType.ACTIVITY_STATUS);

            String workflow = "Workflow1";
            String status = "Registered";

            var eventDto = new EventConfigurationDto(
                    1L,
                    "label",
                    EventTriggerType.ACTIVITY_STATUS,
                    EventActionType.UPDATE_CUSTOM_WORKFLOW,
                    0, true, null, null, null, 1, null,
                    null, 1L, null, null, null, null, null, null, null,
                    null, null, null, 1L,
                    null, null, null, null, null, null,
                    workflow, status, false, null, null, null);
            var event = new EventConfiguration(eventDto);
            var updateCustomWorkflowEventAction = new UpdateCustomWorkflowEventAction(event, eventDto, new TestTaskPublisher());
            updateCustomWorkflowEventAction.doAction(null, handle, signal);

            assertEquals(EventActionType.UPDATE_CUSTOM_WORKFLOW.name(), expectedTaskName);
            assertEquals("{\"workflow\":\"" + workflow + "\",\"status\":\"" + status + "\"}", expectedEventPayload);
            assertEquals(testData.getStudyGuid(), expectedStudyGuid);
            assertEquals(testData.getUserGuid(), expectedParticipantGuid);

            handle.rollback();
        });
    }

    static class TestTaskPublisher implements TaskPublisher {

        @Override
        public void publishTask(String taskName, String payload, String studyGuid, String participantGuid) {
            expectedTaskName = taskName;
            expectedEventPayload = payload;
            expectedStudyGuid = studyGuid;
            expectedParticipantGuid = participantGuid;
        }
    }
}
