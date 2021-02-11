package org.broadinstitute.ddp.event.pubsubtask.api;

import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_TASK_TYPE;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult.ATTR_TASK_MESSAGE_ID;
import static org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile.UpdateProfileConstants.ATTR_PARTICIPANT_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile.UpdateProfileConstants.ATTR_STUDY_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile.UpdateProfileConstants.ATTR_USER_ID;


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
                .putAttributes(ATTR_TASK_MESSAGE_ID, pubSubTaskResult.getPubSubTask().getMessageId())
                .putAttributes(ATTR_TASK_TYPE, pubSubTaskResult.getPubSubTask().getTaskType())
                .putAttributes(ATTR_PARTICIPANT_GUID, pubSubTaskResult.getPubSubTask().getAttributes().get(ATTR_PARTICIPANT_GUID))
                .putAttributes(ATTR_USER_ID, pubSubTaskResult.getPubSubTask().getAttributes().get(ATTR_USER_ID))
                .putAttributes(ATTR_STUDY_GUID, pubSubTaskResult.getPubSubTask().getAttributes().get(ATTR_STUDY_GUID));
        return messageBuilder.build();
    }

}
