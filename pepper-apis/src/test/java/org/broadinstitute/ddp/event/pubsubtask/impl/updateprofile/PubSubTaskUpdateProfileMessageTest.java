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
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.TEST_PARTICIPANT_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.TEST_STUDY_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.TEST_USER_ID;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.buildMessage;
import static org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile.UpdateProfileProcessor.TASK_TYPE__UPDATE_PROFILE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;


import com.google.pubsub.v1.ProjectSubscriptionName;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskMessageParser;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskProcessorFactory;
import org.broadinstitute.ddp.event.pubsubtask.impl.PubSubTaskProcessorFactoryImpl;
import org.junit.Test;

public class PubSubTaskUpdateProfileMessageTest {

    private final ProjectSubscriptionName projectSubscriptionName =
            ProjectSubscriptionName.of(PROJECT_ID, PUBSUB_SUBSCRIPTION);
    private PubSubTaskProcessorFactory testFactory;
    private PubSubTaskMessageParser messageParser;


    @Test
    public void testUpdateProfileValidMessageParser() {
        buildMessageAndAssert(true);
    }

    @Test
    public void testUpdateProfileInvalidMessageParser() {
        var result = buildMessageAndAssert(false);
        assertEquals("PUBSUB_TASK error: Some attributes are not specified in the pubsub message "
                + "[id=msg_id,taskType=UPDATE_PROFILE]: participantGuid=null, userId=null", result.getErrorMessage());
    }

    @Test
    public void testUpdateProfileEmptyBodyMessageParser() {
        init();
        var message = buildMessage(TASK_TYPE__UPDATE_PROFILE, "", true);
        var result = messageParser.parseMessage(message);
        assertEquals("PUBSUB_TASK error: Empty payload in the pubsub message [id=msg_id,taskType=UPDATE_PROFILE]",
                result.getErrorMessage());
    }

    private PubSubTaskMessageParser.PubSubTaskMessageParseResult buildMessageAndAssert(boolean buildValidMessage) {
        init();
        var message = buildMessage(TASK_TYPE__UPDATE_PROFILE,
                format("{'%s':'%s', '%s':'%s', '%s':'%s'}",
                        EMAIL, TEST_EMAIL, FIRST_NAME, TEST_FIRST_NAME, LAST_NAME, TEST_LAST_NAME), buildValidMessage);
        var result = messageParser.parseMessage(message);

        if (buildValidMessage) {
            assertEquals(TEST_PARTICIPANT_GUID, result.getPubSubTask().getParticipantGuid());
            assertEquals(TEST_USER_ID, result.getPubSubTask().getUserId());
            assertEquals(TEST_STUDY_GUID, result.getPubSubTask().getStudyGuid());
        }
        assertEquals(TEST_EMAIL, result.getPubSubTask().getPayloadMap().getMap().get(EMAIL));
        assertEquals(TEST_FIRST_NAME, result.getPubSubTask().getPayloadMap().getMap().get(FIRST_NAME));
        assertEquals(TEST_LAST_NAME, result.getPubSubTask().getPayloadMap().getMap().get(LAST_NAME));
        assertNull(result.getPubSubTask().getPayloadObject());

        return result;
    }

    private void init() {
        testFactory = new PubSubTaskProcessorFactoryImpl();
        messageParser = new PubSubTaskMessageParser(projectSubscriptionName, testFactory);
    }
}
