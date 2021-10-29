package org.broadinstitute.ddp.event.pubsubtask.impl.userdelete;


import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.TEST_MESSAGE_ID;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.TEST_PARTICIPANT_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.TEST_STUDY_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.buildMessage;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_NAME__PARTICIPANT_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_NAME__STUDY_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_NAME__TASK_TYPE;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult.ATTR_TASK__MESSAGE_ID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult.PubSubTaskResultType.ERROR;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult.PubSubTaskResultType.SUCCESS;
import static org.broadinstitute.ddp.event.pubsubtask.impl.userdelete.UserDeleteProcessor.TASK_TYPE__USER_DELETE;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult;
import org.broadinstitute.ddp.event.pubsubtask.impl.PubSubTaskMessageTestAbstract;
import org.junit.Test;

/**
 * Tests to verify processing PubSub task of type {@link UserDeleteProcessor#TASK_TYPE__USER_DELETE}
 */
public class PubSubTaskUserDeleteMessageTest extends PubSubTaskMessageTestAbstract {

    @Test
    public void testUserDeleteValidMessageParser() {
        buildMessageAndAssert(true);

        PubSubTaskResult result = testResultSender.getPubSubTaskResult();

        assertEquals("taskType=USER_DELETE, messageId=msg_id, attr={taskType=USER_DELETE, "
                + "participantGuid=participant_guid, studyGuid=study_guid}, payload={}",
                result.getPubSubTask().toString());
        assertEquals("", result.getPubSubTask().getPayloadJson());
        assertEquals(SUCCESS, result.getResultType());
        assertEquals(4, result.getAttributes().size());
        assertEquals(TASK_TYPE__USER_DELETE, result.getAttributes().get(ATTR_NAME__TASK_TYPE));
        assertEquals(TEST_PARTICIPANT_GUID, result.getAttributes().get(ATTR_NAME__PARTICIPANT_GUID));
        assertEquals(TEST_STUDY_GUID, result.getAttributes().get(ATTR_NAME__STUDY_GUID));
        assertEquals(TEST_MESSAGE_ID, result.getAttributes().get(ATTR_TASK__MESSAGE_ID));
        assertEquals("{\"resultType\":\"SUCCESS\"}", result.getPayloadJson());
    }

    @Test
    public void testUserDeleteInvalidMessageParser() {
        buildMessageAndAssert(false);

        assertEquals(ERROR, testResultSender.getPubSubTaskResult().getResultType());
        assertEquals("PubSubTask 'USER_DELETE' processing FAILED: attribute participantGuid is incorrect: [null]",
                testResultSender.getPubSubTaskResult().getErrorMessage());
    }

    private void buildMessageAndAssert(boolean buildValidMessage) {
        init();
        var message = buildMessage(TASK_TYPE__USER_DELETE, null, buildValidMessage, null);
        pubSubTaskReceiver.receiveMessage(message, mock(AckReplyConsumer.class));
    }
}
