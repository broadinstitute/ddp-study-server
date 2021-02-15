package org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile;


import static java.lang.String.format;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.EMAIL;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.FIRST_NAME;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.LAST_NAME;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.PROJECT_ID;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.PUBSUB_SUBSCRIPTION;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.TEST_EMAIL;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.TEST_FIRST_NAME;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.TEST_LAST_NAME;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.buildMessage;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult.PubSubTaskResultType.ERROR;
import static org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile.UpdateProfileConstants.TASK_TYPE__UPDATE_PROFILE;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.Map;


import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.gson.Gson;
import com.google.pubsub.v1.ProjectSubscriptionName;
import org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil;
import org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.TestResultSender;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskReceiver;
import org.broadinstitute.ddp.util.GsonUtil;
import org.junit.Test;

public class PubSubTaskUpdateProfileMessageTest {

    private final ProjectSubscriptionName projectSubscriptionName =
            ProjectSubscriptionName.of(PROJECT_ID, PUBSUB_SUBSCRIPTION);
    private PubSubTaskReceiver pubSubTaskReceiver;
    private TestResultSender testResultSender;

    private final Gson gson = GsonUtil.standardGson();


    @Test
    public void testUpdateProfileValidMessageParser() {
        buildMessageAndAssert(true);

        assertEquals("{'email':'test@datadonationplatform.org', 'firstName':'Lorenzo', 'lastName':'Montana'}",
                testResultSender.getPubSubTaskResult().getPubSubTask().getPayloadJson());
        Map<String, String> payload = gson.fromJson(testResultSender.getPubSubTaskResult().getPubSubTask().getPayloadJson(), Map.class);
        assertEquals(TEST_EMAIL, payload.get(EMAIL));
        assertEquals(TEST_FIRST_NAME, payload.get(FIRST_NAME));
        assertEquals(TEST_LAST_NAME, payload.get(LAST_NAME));
    }

    @Test
    public void testUpdateProfileInvalidMessageParser() {
        buildMessageAndAssert(false);

        assertEquals("{'email':'test@datadonationplatform.org', 'firstName':'Lorenzo', 'lastName':'Montana'}",
                testResultSender.getPubSubTaskResult().getPubSubTask().getPayloadJson());
        assertEquals(ERROR, testResultSender.getPubSubTaskResult().getResultType());
        assertEquals("Error processing taskType=UPDATE_PROFILE - some attributes are not specified: "
                        + "participantGuid=null, userId=null",
                testResultSender.getPubSubTaskResult().getErrorMessage());
    }

    @Test
    public void testUpdateProfileEmptyBodyMessageParser() {
        init();
        var message = buildMessage(TASK_TYPE__UPDATE_PROFILE, "", true);

        pubSubTaskReceiver.receiveMessage(message, mock(AckReplyConsumer.class));

        assertEquals(4, testResultSender.getPubSubTaskResult().getPubSubTask().getAttributes().size());
        assertEquals("", testResultSender.getPubSubTaskResult().getPubSubTask().getPayloadJson());
        assertEquals(ERROR, testResultSender.getPubSubTaskResult().getResultType());
        assertEquals("Error processing taskType=%s: empty payload",
                testResultSender.getPubSubTaskResult().getErrorMessage());
    }

    private void buildMessageAndAssert(boolean buildValidMessage) {
        init();
        var message = buildMessage(TASK_TYPE__UPDATE_PROFILE,
                format("{'%s':'%s', '%s':'%s', '%s':'%s'}",
                        EMAIL, TEST_EMAIL, FIRST_NAME, TEST_FIRST_NAME, LAST_NAME, TEST_LAST_NAME), buildValidMessage);
        pubSubTaskReceiver.receiveMessage(message, mock(AckReplyConsumer.class));
    }

    private void init() {
        testResultSender = new TestResultSender();
        pubSubTaskReceiver = new PubSubTaskReceiver(projectSubscriptionName,
                new PubSubTaskTestUtil.TestTaskProcessorFactory(), testResultSender);
    }
}
