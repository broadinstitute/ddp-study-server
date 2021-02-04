package org.broadinstitute.ddp.event.dsmtask.api;

import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskData.ATTR_PARTICIPANT_GUID;
import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskData.ATTR_STUDY_GUID;
import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskData.ATTR_TASK_TYPE;
import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskData.ATTR_USER_ID;
import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskLogUtil.errorMsg;
import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskLogUtil.infoMsg;
import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskResultData.ATTR_TASK_MESSAGE_ID;
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

public class DsmTaskResultSender {

    private static final Logger LOG = getLogger(DsmTaskResultSender.class);

    private final Publisher publisher;
    private final Gson gson = GsonUtil.standardGson();

    public DsmTaskResultSender(Publisher publisher) {
        this.publisher = publisher;
    }

    public void sendDsmTaskResult(DsmTaskResultData dsmTaskResultData) {

        ApiFuture<String> publishResult = publisher.publish(createPubSubMessage(dsmTaskResultData));

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
                        String msg = errorMsg("failed to send DsmTask response \"" + dsmTaskResultData + "\"");
                        if (statusCode != null) {
                            msg += ", statusCode=" + statusCode;
                        }
                        LOG.error(msg, e);
                    }

                    @Override
                    public void onSuccess(String messageId) {
                        LOG.info(infoMsg("Result \"{}\" successfully published to pubsub topic={}. MessageId={}"),
                                dsmTaskResultData, publisher.getTopicName(), messageId);
                    }
                },
                MoreExecutors.directExecutor()
        );
    }

    private PubsubMessage createPubSubMessage(DsmTaskResultData dsmTaskResultData) {
        var messageJson = gson.toJson(dsmTaskResultData.getDsmTaskResultPayload());
        PubsubMessage.Builder messageBuilder = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(messageJson))
                .putAttributes(ATTR_TASK_MESSAGE_ID, dsmTaskResultData.getDsmTaskData().getMessageId())
                .putAttributes(ATTR_TASK_TYPE, dsmTaskResultData.getDsmTaskData().getTaskType())
                .putAttributes(ATTR_PARTICIPANT_GUID, dsmTaskResultData.getDsmTaskData().getParticipantGuid())
                .putAttributes(ATTR_USER_ID, dsmTaskResultData.getDsmTaskData().getUserId())
                .putAttributes(ATTR_STUDY_GUID, dsmTaskResultData.getDsmTaskData().getStudyGuid());
        return messageBuilder.build();
    }
}
