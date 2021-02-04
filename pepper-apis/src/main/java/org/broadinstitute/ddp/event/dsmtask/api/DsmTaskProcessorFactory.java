package org.broadinstitute.ddp.event.dsmtask.api;

/**
 * Factory for registering {@link DsmTaskProcessor}'s
 * for each of known DsmTask taskType's.
 * Currently implemented a type UPDATE_PROFILE.
 */
public interface DsmTaskProcessorFactory {

    DsmTaskDescriptor getDsmTaskDescriptors(String dsmTaskType);

    class DsmTaskDescriptor {
        private final DsmTaskProcessor dsmTaskProcessor;
        private final Class<?> payloadClass;

        public DsmTaskDescriptor(DsmTaskProcessor dsmTaskProcessor, Class<?> payloadClass) {
            this.dsmTaskProcessor = dsmTaskProcessor;
            this.payloadClass = payloadClass;
        }

        public DsmTaskProcessor getDsmTaskProcessor() {
            return dsmTaskProcessor;
        }

        public Class<?> getPayloadClass() {
            return payloadClass;
        }
    }
}
