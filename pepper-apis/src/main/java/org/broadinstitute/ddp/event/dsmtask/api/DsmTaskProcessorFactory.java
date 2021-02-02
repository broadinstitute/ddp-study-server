package org.broadinstitute.ddp.event.dsmtask.api;

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
