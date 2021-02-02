package org.broadinstitute.ddp.event.dsmtask.api;

import java.util.HashMap;
import java.util.Map;

public abstract class DsmTaskProcessorFactoryAbstract implements DsmTaskProcessorFactory {

    protected final Map<String, DsmTaskDescriptor> dsmTaskDescriptors = new HashMap<>();

    protected  void registerDsmTaskProcessors(
            String dsmTaskType,
            DsmTaskProcessor dsmTaskProcessor,
            Class<?> payloadClass) {
        dsmTaskDescriptors.put(dsmTaskType, new DsmTaskDescriptor(dsmTaskProcessor, payloadClass));
    }

    protected abstract void registerDsmTaskProcessors();

    @Override
    public DsmTaskDescriptor getDsmTaskDescriptors(String dsmTaskType) {
        return dsmTaskDescriptors.get(dsmTaskType);
    }
}
