package org.broadinstitute.ddp.event.pubsubtask;

import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_NAME__PARTICIPANT_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_NAME__STUDY_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_NAME__TASK_TYPE;
import static org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile.UpdateProfileConstants.ATTR_NAME__USER_ID;
import static org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile.UpdateProfileConstants.TASK_TYPE__UPDATE_PROFILE;
import static org.broadinstitute.ddp.event.pubsubtask.impl.userdelete.UserDeleteConstants.TASK_TYPE__USER_DELETE;

import java.util.Map;

import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskProcessorFactoryAbstract;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult;
import org.broadinstitute.ddp.event.pubsubtask.api.ResultSender;
import org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile.UpdateProfileProcessor;
import org.broadinstitute.ddp.event.pubsubtask.impl.userdelete.UserDeleteProcessor;

public class PubSubTaskTestUtil {

    public static final String PROJECT_ID = "projectId";
    public static final String PUBSUB_SUBSCRIPTION = "pubSubSubscription";

    public static final String TEST_MESSAGE_ID = "msg_id";
    public static final String TEST_PARTICIPANT_GUID = "participant_guid";
    public static final String TEST_USER_ID = "user_id";
    public static final String TEST_STUDY_GUID = "study_guid";

    public static final String EMAIL = "email";
    public static final String EDUCATION = "education";
    public static final String MARITAL_STATUS = "maritalStatus";
    public static final String FIRST_NAME = "firstName";
    public static final String LAST_NAME = "lastName";
    public static final String DO_NOT_CONTACT = "doNotContact";

    public static final String TEST_EMAIL = "test@datadonationplatform.org";
    public static final String TEST_EDUCATION = "University";
    public static final String TEST_MARITAL_STATUS = "Married";
    public static final String TEST_FIRST_NAME = "Lorenzo";
    public static final String TEST_LAST_NAME = "Montana";



    public static PubsubMessage buildMessage(
            String taskType,
            Map<String, String> extraAttibutes,
            String payloadJson,
            boolean buildValidMessage,
            String testUserId) {
        var messageBuilder = PubsubMessage.newBuilder()
                .setMessageId(TEST_MESSAGE_ID)
                .putAttributes(ATTR_NAME__TASK_TYPE, taskType);
        if (payloadJson != null) {
            messageBuilder.setData(ByteString.copyFromUtf8(payloadJson));
        }
        if (buildValidMessage) {
            // set standard attributes
            messageBuilder
                    .putAttributes(ATTR_NAME__PARTICIPANT_GUID, TEST_PARTICIPANT_GUID)
                    .putAttributes(ATTR_NAME__STUDY_GUID, TEST_STUDY_GUID);
            if (extraAttibutes != null) {
                extraAttibutes.forEach((k, v) -> messageBuilder.putAttributes(k, v));
            }
            if (testUserId != null) {
                messageBuilder.putAttributes(ATTR_NAME__USER_ID, testUserId);
            }
        }
        return messageBuilder.build();
    }

    public static class TestResultSender implements ResultSender {
        private PubSubTaskResult pubSubTaskResult;

        @Override
        public void sendPubSubTaskResult(PubSubTaskResult pubSubTaskResult) {
            this.pubSubTaskResult = pubSubTaskResult;
        }

        public PubSubTaskResult getPubSubTaskResult() {
            return pubSubTaskResult;
        }
    }

    public static class TestTaskProcessorFactory extends PubSubTaskProcessorFactoryAbstract {

        @Override
        protected void registerPubSubTaskProcessors() {
            registerPubSubTaskProcessor(
                    TASK_TYPE__UPDATE_PROFILE,
                    new TestUpdateProfileProcessor()
            );
            registerPubSubTaskProcessor(
                    TASK_TYPE__USER_DELETE,
                    new TestUserDeleteProcessor()
            );
        }

        class TestUpdateProfileProcessor extends UpdateProfileProcessor {
            @Override
            public void handleTask(PubSubTask pubSubTask) {
            }
        }

        class TestUserDeleteProcessor extends UserDeleteProcessor {
            @Override
            public void handleTask(PubSubTask pubSubTask) {
            }
        }
    }
}
