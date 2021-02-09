package org.broadinstitute.ddp.event.pubsubtask.api;


import static java.lang.String.format;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.EDUCATION;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.EMAIL;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.MARITAL_STATUS;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.PROJECT_ID;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.PUBSUB_SUBSCRIPTION;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.TEST_EDUCATION;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.TEST_EMAIL;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.TEST_MARITAL_STATUS;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.TEST_MESSAGE_ID;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.TEST_PARTICIPANT_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.TEST_STUDY_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.TEST_USER_ID;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.buildMessage;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_PARTICIPANT_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_STUDY_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_USER_ID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult.ATTR_TASK_MESSAGE_ID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult.PubSubTaskResultType.ERROR;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult.PubSubTaskResultType.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Map;


import com.google.gson.Gson;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import org.broadinstitute.ddp.util.GsonUtil;
import org.junit.Test;

public class PubSubTaskCustomMessageTest {

    private final ProjectSubscriptionName projectSubscriptionName =
            ProjectSubscriptionName.of(PROJECT_ID, PUBSUB_SUBSCRIPTION);
    private PubSubTaskProcessorFactory testFactory;
    private PubSubTaskMessageParser taskMessageParser;
    private PubSubTaskResultMessageCreator resultMessageCreator;

    private final Gson gson = GsonUtil.standardGson();

    /**
     * Verify that if processor registered with payloadClass=null, parse message,
     * check that payload can be converted to a map and properties fetched from it.
     * Create result pubsub message (successful one) out of the parsed {@link PubSubTask}
     */
    @Test
    public void testCustomMessageProcessing1() {
        init();
        var message = buildMessage(TestProcessor1.TEST_TASK_1,
                format("{'%s':'%s', '%s':'%s', '%s':'%s'}",
                        EMAIL, TEST_EMAIL, EDUCATION, TEST_EDUCATION, MARITAL_STATUS, TEST_MARITAL_STATUS), true);
        var parseResult = taskMessageParser.parseMessage(message);

        assertEquals(TEST_PARTICIPANT_GUID, parseResult.getPubSubTask().getParticipantGuid());
        assertEquals(TEST_USER_ID, parseResult.getPubSubTask().getUserId());
        assertEquals(TEST_STUDY_GUID, parseResult.getPubSubTask().getStudyGuid());

        Map<String, String> payload = gson.fromJson(parseResult.getPubSubTask().getPayloadJson(), Map.class);
        assertEquals(TEST_EMAIL, payload.get(EMAIL));
        assertEquals(TEST_EDUCATION, payload.get(EDUCATION));
        assertEquals(TEST_MARITAL_STATUS, payload.get(MARITAL_STATUS));
        assertNull(parseResult.getPubSubTask().getPayloadObject());

        PubSubTaskResult result = new PubSubTaskResult(SUCCESS, null, parseResult.getPubSubTask());
        PubsubMessage resultMessage = resultMessageCreator.createPubSubMessage(result);

        assertEquals(TEST_MESSAGE_ID, resultMessage.getAttributesOrDefault(ATTR_TASK_MESSAGE_ID, null));
        assertEquals(TEST_PARTICIPANT_GUID, resultMessage.getAttributesOrDefault(ATTR_PARTICIPANT_GUID, null));
        assertEquals(TEST_USER_ID, resultMessage.getAttributesOrDefault(ATTR_USER_ID, null));
        assertEquals(TEST_STUDY_GUID, resultMessage.getAttributesOrDefault(ATTR_STUDY_GUID, null));
        assertEquals("{\"resultType\":\"SUCCESS\",\"errorMessage\":null}", resultMessage.getData().toStringUtf8());
    }

    /**
     * Verify that in case if created a processor with specified payload class
     * then payload data will be copied to instance of this class.
     */
    @Test
    public void testCustomMessageProcessing2() {
        init();
        var message = buildMessage(TestProcessor2.TEST_TASK_2,
                format("{'%s':'%s', '%s':'%s'}", EMAIL, TEST_EMAIL, EDUCATION, TEST_EDUCATION), true);
        var parseResult = taskMessageParser.parseMessage(message);

        assertEquals(TEST_PARTICIPANT_GUID, parseResult.getPubSubTask().getParticipantGuid());
        assertEquals(TEST_USER_ID, parseResult.getPubSubTask().getUserId());
        assertEquals(TEST_STUDY_GUID, parseResult.getPubSubTask().getStudyGuid());
        assertNotNull(parseResult.getPubSubTask().getPayloadObject());
        TestPayload2 payload = (TestPayload2) parseResult.getPubSubTask().getPayloadObject();
        assertEquals(TEST_EMAIL, payload.getEmail());
        assertEquals(TEST_EDUCATION, payload.getEducation());

        PubSubTaskResult result = new PubSubTaskResult(ERROR, "Custom error occured", parseResult.getPubSubTask());
        PubsubMessage resultMessage = resultMessageCreator.createPubSubMessage(result);
        assertEquals("{\"resultType\":\"ERROR\",\"errorMessage\":\"Custom error occured\"}",
                resultMessage.getData().toStringUtf8());
    }

    private void init() {
        testFactory = new TestFactory();
        taskMessageParser = new PubSubTaskMessageParser(projectSubscriptionName, testFactory);
        resultMessageCreator = new PubSubTaskResultMessageCreator();
    }

    class TestProcessor1 extends PubSubTaskProcessorAbstract {

        static final String TEST_TASK_1 = "TEST_TASK_1";

        @Override
        protected void handleTask(PubSubTask pubSubTask) {
        }
    }

    class TestProcessor2 extends PubSubTaskProcessorAbstract {

        static final String TEST_TASK_2 = "TEST_TASK_2";

        @Override
        protected void handleTask(PubSubTask pubSubTask) {
        }
    }

    class TestPayload2 {
        String email;
        String education;
        String maritalStatus;

        public TestPayload2(String email, String education, String maritalStatus) {
            this.email = email;
            this.education = education;
            this.maritalStatus = maritalStatus;
        }

        public String getEmail() {
            return email;
        }

        public String getEducation() {
            return education;
        }

        public String getMaritalStatus() {
            return maritalStatus;
        }
    }

    class TestFactory extends PubSubTaskProcessorFactoryAbstract {
        @Override
        protected void registerPubSubTaskProcessors() {
            registerPubSubTaskProcessors(
                    TestProcessor1.TEST_TASK_1,
                    new TestProcessor1(),
                    null
            );
            registerPubSubTaskProcessors(
                    TestProcessor2.TEST_TASK_2,
                    new TestProcessor2(),
                    TestPayload2.class
            );
        }
    }
}
