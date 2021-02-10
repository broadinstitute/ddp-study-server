package org.broadinstitute.ddp.event.pubsubtask.api;

import java.util.Properties;


import com.google.gson.Gson;
import org.broadinstitute.ddp.util.GsonUtil;


public abstract class PubSubTaskDataReaderAbstract implements PubSubTaskDataReader {

    private final Gson gson = GsonUtil.standardGson();

    @Override
    public PubSubTaskPayloadData readTaskData(PubSubTask pubSubTask, Class<?> payloadClass) {
        Object payloadObject = null;
        if (payloadClass != null) {
            payloadObject = gson.fromJson(pubSubTask.getPayloadJson(), payloadClass);
        }

        Properties payloadProperties = gson.fromJson(pubSubTask.getPayloadJson(), Properties.class);

        return new PubSubTaskPayloadData(payloadProperties, payloadObject);
    }
}
