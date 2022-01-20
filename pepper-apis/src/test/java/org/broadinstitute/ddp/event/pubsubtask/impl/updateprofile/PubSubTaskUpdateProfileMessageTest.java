package org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile;


import static java.lang.String.format;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.DO_NOT_CONTACT;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.EMAIL;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.FIRST_NAME;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.LAST_NAME;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.TEST_EMAIL;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.TEST_FIRST_NAME;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.TEST_LAST_NAME;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.TEST_USER_ID;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.buildMessage;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult.PubSubTaskResultType.ERROR;
import static org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile.UpdateProfileConstants.TASK_TYPE__UPDATE_PROFILE;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.Map;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import org.broadinstitute.ddp.event.pubsubtask.impl.PubSubTaskMessageTestAbstract;
import org.junit.Test;


/**
 * Tests to verify processing PubSub task of type {@link UpdateProfileConstants#TASK_TYPE__UPDATE_PROFILE}
 */
public class PubSubTaskUpdateProfileMessageTest extends PubSubTaskMessageTestAbstract {

    @Test
    public void testUpdateProfileValidMessageParser() {
        buildMessageAndAssert(true);

        assertEquals("{'email':'test@datadonationplatform.org', 'firstName':'Lorenzo', 'lastName':'Montana', 'doNotContact':'true'}",
                testResultSender.getPubSubTaskResult().getPubSubTask().getPayloadJson());
        Map<String, String> payload = gson.fromJson(testResultSender.getPubSubTaskResult().getPubSubTask().getPayloadJson(), Map.class);
        assertEquals(TEST_EMAIL, payload.get(EMAIL));
        assertEquals(TEST_FIRST_NAME, payload.get(FIRST_NAME));
        assertEquals(TEST_LAST_NAME, payload.get(LAST_NAME));
    }

    @Test
    public void testUpdateProfileInvalidMessageParser() {
        buildMessageAndAssert(false);

        assertEquals("{'email':'test@datadonationplatform.org', 'firstName':'Lorenzo', 'lastName':'Montana', 'doNotContact':'true'}",
                testResultSender.getPubSubTaskResult().getPubSubTask().getPayloadJson());
        assertEquals(ERROR, testResultSender.getPubSubTaskResult().getResultType());
        assertEquals("PubSubTask 'UPDATE_PROFILE' processing FAILED, some attributes are not specified: "
                        + "participantGuid=null, userId=null",
                testResultSender.getPubSubTaskResult().getErrorMessage());
    }

    @Test
    public void testUpdateProfileEmptyBodyMessageParser() {
        init();
        var message = buildMessage(TASK_TYPE__UPDATE_PROFILE, null, "", true, TEST_USER_ID);

        pubSubTaskReceiver.receiveMessage(message, mock(AckReplyConsumer.class));

        assertEquals(4, testResultSender.getPubSubTaskResult().getPubSubTask().getAttributes().size());
        assertEquals("", testResultSender.getPubSubTaskResult().getPubSubTask().getPayloadJson());
        assertEquals(ERROR, testResultSender.getPubSubTaskResult().getResultType());
        assertEquals("PubSubTask processing FAILED: empty payload",
                testResultSender.getPubSubTaskResult().getErrorMessage());
    }

    private void buildMessageAndAssert(boolean buildValidMessage) {
        init();
        var message = buildMessage(TASK_TYPE__UPDATE_PROFILE, null,
                format("{'%s':'%s', '%s':'%s', '%s':'%s', '%s':'%s'}",
                        EMAIL, TEST_EMAIL, FIRST_NAME, TEST_FIRST_NAME, LAST_NAME, TEST_LAST_NAME,
                        DO_NOT_CONTACT, true), buildValidMessage, TEST_USER_ID);
        pubSubTaskReceiver.receiveMessage(message, mock(AckReplyConsumer.class));
    }
}
