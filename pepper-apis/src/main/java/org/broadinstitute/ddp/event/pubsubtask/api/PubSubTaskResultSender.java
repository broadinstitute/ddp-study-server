package org.broadinstitute.ddp.event.pubsubtask.api;

import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_PARTICIPANT_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_STUDY_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_TASK_TYPE;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_USER_ID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskLogUtil.errorMsg;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskLogUtil.infoMsg;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult.ATTR_TASK_MESSAGE_ID;
import static org.slf4j.LoggerFactory.getLogger;


import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import org.broadinstitute.ddp.util.GsonUtil;
import org.slf4j.Logger;

/**
 * Builds PubSubTaskResult-message and publishes it
 * to outgoing topic (name specified in Config parameter
 * "pubsub.pubSubTasksResultTopic").
 */
public class PubSubTaskResultSender {

    private static final Logger LOG = getLogger(PubSubTaskResultSender.class);

    private final Publisher publisher;
    private final Gson gson = GsonUtil.standardGson();

    public PubSubTaskResultSender(Publisher publisher) {
        this.publisher = publisher;
    }

    public void sendPubSubTaskResult(PubSubTaskResult pubSubTaskResult) {

        ApiFuture<String> publishResult = publisher.publish(createPubSubMessage(pubSubTaskResult));

        ApiFutures.addCallback(
                publishResult,
                new ApiFutureCallback<>() {

                    @Override
                    public void onFailure(Throwable e) {
                        String statusCode = null;
                        if (e instanceof ApiException) {
                            ApiException apiException = ((ApiException) e);
                            statusCode = apiException.getStatusCode().getCode().toString();
                        }
                        String msg = errorMsg("Failed to send PubSubTask response \"" + pubSubTaskResult + "\"");
                        if (statusCode != null) {
                            msg += ", statusCode=" + statusCode;
                        }
                        LOG.error(msg, e);
                    }

                    @Override
                    public void onSuccess(String messageId) {
                        LOG.info(infoMsg("Result \"{}\" successfully published to pubsub topic={}. MessageId={}"),
                                pubSubTaskResult, publisher.getTopicName(), messageId);
                    }
                },
                MoreExecutors.directExecutor()
        );
    }

    private PubsubMessage createPubSubMessage(PubSubTaskResult pubSubTaskResult) {
        var messageJson = gson.toJson(pubSubTaskResult.getPubSubTaskResultPayload());
        PubsubMessage.Builder messageBuilder = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(messageJson))
                .putAttributes(ATTR_TASK_MESSAGE_ID, pubSubTaskResult.getPubSubTaskMessage().getMessageId())
                .putAttributes(ATTR_TASK_TYPE, pubSubTaskResult.getPubSubTaskMessage().getTaskType())
                .putAttributes(ATTR_PARTICIPANT_GUID, pubSubTaskResult.getPubSubTaskMessage().getParticipantGuid())
                .putAttributes(ATTR_USER_ID, pubSubTaskResult.getPubSubTaskMessage().getUserId())
                .putAttributes(ATTR_STUDY_GUID, pubSubTaskResult.getPubSubTaskMessage().getStudyGuid());
        return messageBuilder.build();
    }
}
