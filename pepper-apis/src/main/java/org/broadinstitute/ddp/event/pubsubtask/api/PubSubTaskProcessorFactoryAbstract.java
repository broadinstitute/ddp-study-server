package org.broadinstitute.ddp.event.pubsubtask.api;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstract implementation of processors factory. It provides protected method
 * which should be used for {@link PubSubTaskProcessor}-s registration:
 * <pre>
 * {@link #registerPubSubTaskProcessors()} - this should be overridden;
 * {@link #registerPubSubTaskProcessor(String, PubSubTaskProcessor)} - this should be called
 *   within {@link #registerPubSubTaskProcessors()} for registering a certain processor.
 * </pre>
 */
public abstract class PubSubTaskProcessorFactoryAbstract implements PubSubTaskProcessorFactory {

    protected final Map<String, PubSubTaskDescriptor> pubSubTaskDescriptors = new HashMap<>();

    public PubSubTaskProcessorFactoryAbstract() {
        registerPubSubTaskProcessors();
    }

    protected  void registerPubSubTaskProcessor(String pubSubTaskType, PubSubTaskProcessor pubSubTaskProcessor) {
        pubSubTaskDescriptors.put(pubSubTaskType, new PubSubTaskDescriptor(pubSubTaskProcessor));
    }

    protected abstract void registerPubSubTaskProcessors();

    @Override
    public PubSubTaskDescriptor getPubSubTaskDescriptor(String pubSubTaskType) {
        return pubSubTaskDescriptors.get(pubSubTaskType);
    }
}
