package org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile;


import static java.lang.String.format;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskLogUtil.errorMsg;
import static org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile.UpdateProfileConstants.ATTR_PARTICIPANT_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile.UpdateProfileConstants.ATTR_USER_ID;


import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskDataReaderAbstract;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskException;

public class UpdateProfileDataReader extends PubSubTaskDataReaderAbstract {

    @Override
    public PubSubTaskPayloadData readTaskData(PubSubTask pubSubTask, Class<?> payloadClass) {
        String participantGuid = pubSubTask.getAttributes().get(ATTR_PARTICIPANT_GUID);
        String userId = pubSubTask.getAttributes().get(ATTR_USER_ID);
        if (participantGuid == null || userId == null) {
            throw new PubSubTaskException(format(errorMsg("Some attributes are not specified: participantGuid=%s, userId=%s"),
                    participantGuid, userId), pubSubTask);
        }
        if (StringUtils.isBlank(pubSubTask.getPayloadJson())) {
            throw new PubSubTaskException(errorMsg("Empty payload"), pubSubTask);
        }

        return super.readTaskData(pubSubTask, payloadClass);
    }
}
