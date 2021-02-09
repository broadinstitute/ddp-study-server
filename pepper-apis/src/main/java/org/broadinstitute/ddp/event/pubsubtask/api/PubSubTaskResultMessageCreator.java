package org.broadinstitute.ddp.event.pubsubtask.api;

import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_PARTICIPANT_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_STUDY_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_TASK_TYPE;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_USER_ID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult.ATTR_TASK_MESSAGE_ID;


import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import org.broadinstitute.ddp.util.GsonUtil;


public class PubSubTaskResultMessageCreator {

    private final Gson gson = GsonUtil.standardGson();

    public PubsubMessage createPubSubMessage(PubSubTaskResult pubSubTaskResult) {
        var messageJson = gson.toJson(pubSubTaskResult.getPubSubTaskResultPayload());
        var messageBuilder = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(messageJson))
                .putAttributes(ATTR_TASK_MESSAGE_ID, pubSubTaskResult.getPubSubTaskMessage().getMessageId())
                .putAttributes(ATTR_TASK_TYPE, pubSubTaskResult.getPubSubTaskMessage().getTaskType())
                .putAttributes(ATTR_PARTICIPANT_GUID, pubSubTaskResult.getPubSubTaskMessage().getParticipantGuid())
                .putAttributes(ATTR_USER_ID, pubSubTaskResult.getPubSubTaskMessage().getUserId())
                .putAttributes(ATTR_STUDY_GUID, pubSubTaskResult.getPubSubTaskMessage().getStudyGuid());
        return messageBuilder.build();
    }

}
