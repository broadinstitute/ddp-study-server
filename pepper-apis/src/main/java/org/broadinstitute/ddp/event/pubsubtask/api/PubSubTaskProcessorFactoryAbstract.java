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
            Class<?> payloadClass,
            boolean payloadConvertibleToMap) {
        pubSubTaskDescriptors.put(pubSubTaskType, new PubSubTaskDescriptor(pubSubTaskProcessor, payloadClass, payloadConvertibleToMap));
    }

    protected abstract void registerPubSubTaskProcessors();

    @Override
    public PubSubTaskDescriptor getPubSubTaskDescriptors(String pubSubTaskType) {
        return pubSubTaskDescriptors.get(pubSubTaskType);
    }
}
