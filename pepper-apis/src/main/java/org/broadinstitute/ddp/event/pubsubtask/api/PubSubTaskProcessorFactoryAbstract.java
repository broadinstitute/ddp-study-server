package org.broadinstitute.ddp.event.pubsubtask.api;

import java.util.HashMap;
import java.util.Map;

public abstract class PubSubTaskProcessorFactoryAbstract implements PubSubTaskProcessorFactory {

    protected final Map<String, PubSubTaskDescriptor> pubSubTaskDescriptors = new HashMap<>();

    public PubSubTaskProcessorFactoryAbstract() {
        registerPubSubTaskProcessors();
    }

    protected  void registerPubSubTaskProcessors(
            String pubSubTaskType,
            PubSubTaskProcessor pubSubTaskProcessor,
            PubSubTaskDataReader pubSubTaskDataReader,
            Class<?> payloadClass) {
        pubSubTaskDescriptors.put(pubSubTaskType,
                new PubSubTaskDescriptor(pubSubTaskProcessor, pubSubTaskDataReader, payloadClass));
    }

    protected abstract void registerPubSubTaskProcessors();

    @Override
    public PubSubTaskDescriptor getPubSubTaskDescriptors(String pubSubTaskType) {
        return pubSubTaskDescriptors.get(pubSubTaskType);
    }
}
