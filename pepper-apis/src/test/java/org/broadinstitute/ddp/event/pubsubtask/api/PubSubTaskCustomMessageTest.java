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
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_NAME__PARTICIPANT_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_NAME__STUDY_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult.ATTR_TASK__MESSAGE_ID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult.PubSubTaskResultType.ERROR;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult.PubSubTaskResultType.SUCCESS;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResultSender.createPubSubMessage;
import static org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile.UpdateProfileConstants.ATTR_NAME__USER_ID;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.Properties;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.gson.Gson;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.junit.Test;

public class PubSubTaskCustomMessageTest {

    private final ProjectSubscriptionName projectSubscriptionName =
            ProjectSubscriptionName.of(PROJECT_ID, PUBSUB_SUBSCRIPTION);
    private PubSubTaskProcessorFactory testFactory;
    private PubSubTaskReceiver pubSubTaskReceiver;
    private PubSubTaskTestUtil.TestResultSender testResultSender;

    private final Gson gson = GsonUtil.standardGson();

    /**
     * Verify that if processor registered with payloadClass=null, parse message,
     * check that payload can be converted to a map and properties fetched from it.
     * Create result pubsub message (successful one) out of the parsed {@link PubSubTask}
     */
    @Test
    public void testCustomMessageProcessing1() {
        init();
        var message = buildMessage(TestProcessor1.TEST_TASK_1, null,
                format("{'%s':'%s', '%s':'%s', '%s':'%s'}",
                        EMAIL, TEST_EMAIL, EDUCATION, TEST_EDUCATION, MARITAL_STATUS, TEST_MARITAL_STATUS), true, TEST_USER_ID);
        pubSubTaskReceiver.receiveMessage(message, mock(AckReplyConsumer.class));

        PubSubTask pubSubTask = testResultSender.getPubSubTaskResult().getPubSubTask();

        assertEquals("{'email':'test@datadonationplatform.org', 'education':'University', 'maritalStatus':'Married'}",
                pubSubTask.getPayloadJson());

        assertEquals(TEST_PARTICIPANT_GUID, pubSubTask.getAttributes().get(ATTR_NAME__PARTICIPANT_GUID));
        assertEquals(TEST_USER_ID, pubSubTask.getAttributes().get(ATTR_NAME__USER_ID));
        assertEquals(TEST_STUDY_GUID, pubSubTask.getAttributes().get(ATTR_NAME__STUDY_GUID));

        Properties payload = gson.fromJson(pubSubTask.getPayloadJson(), Properties.class);
        assertEquals(TEST_EMAIL, payload.get(EMAIL));
        assertEquals(TEST_EDUCATION, payload.get(EDUCATION));
        assertEquals(TEST_MARITAL_STATUS, payload.get(MARITAL_STATUS));

        PubSubTaskResult pubSubTaskResult = new PubSubTaskResult(SUCCESS, null, pubSubTask);
        PubsubMessage resultMessage = createPubSubMessage(pubSubTaskResult);

        assertEquals(TEST_MESSAGE_ID, resultMessage.getAttributesOrDefault(ATTR_TASK__MESSAGE_ID, null));
        assertEquals(TEST_PARTICIPANT_GUID, resultMessage.getAttributesOrDefault(ATTR_NAME__PARTICIPANT_GUID, null));
        assertEquals(TEST_USER_ID, resultMessage.getAttributesOrDefault(ATTR_NAME__USER_ID, null));
        assertEquals(TEST_STUDY_GUID, resultMessage.getAttributesOrDefault(ATTR_NAME__STUDY_GUID, null));
        assertEquals("{\"resultType\":\"SUCCESS\"}", resultMessage.getData().toStringUtf8());
    }

    /**
     * Verify that in case if created a processor with specified payload class
     * then payload data will be copied to instance of this class.
     */
    @Test
    public void testCustomMessageProcessing2() {
        init();
        var message = buildMessage(TestProcessor2.TEST_TASK_2, null,
                format("{'%s':'%s', '%s':'%s'}", EMAIL, TEST_EMAIL, EDUCATION, TEST_EDUCATION), true, TEST_USER_ID);
        pubSubTaskReceiver.receiveMessage(message, mock(AckReplyConsumer.class));

        PubSubTask pubSubTask = testResultSender.getPubSubTaskResult().getPubSubTask();

        assertEquals(TEST_PARTICIPANT_GUID, pubSubTask.getAttributes().get(ATTR_NAME__PARTICIPANT_GUID));
        assertEquals(TEST_USER_ID, pubSubTask.getAttributes().get(ATTR_NAME__USER_ID));
        assertEquals(TEST_STUDY_GUID, pubSubTask.getAttributes().get(ATTR_NAME__STUDY_GUID));

        PubSubTaskResult pubSubTaskResult = new PubSubTaskResult(ERROR, "Custom error occured", pubSubTask);
        PubsubMessage resultMessage = createPubSubMessage(pubSubTaskResult);
        assertEquals("{\"errorMessage\":\"Custom error occured\",\"resultType\":\"ERROR\"}",
                resultMessage.getData().toStringUtf8());
    }

    private void init() {
        testFactory = new TestFactory();
        testResultSender = new PubSubTaskTestUtil.TestResultSender();
        pubSubTaskReceiver = new PubSubTaskReceiver(projectSubscriptionName, testFactory, testResultSender);
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
            registerPubSubTaskProcessor(
                    TestProcessor1.TEST_TASK_1,
                    new TestProcessor1()
            );
            registerPubSubTaskProcessor(
                    TestProcessor2.TEST_TASK_2,
                    new TestProcessor2()
            );
        }
    }
}
