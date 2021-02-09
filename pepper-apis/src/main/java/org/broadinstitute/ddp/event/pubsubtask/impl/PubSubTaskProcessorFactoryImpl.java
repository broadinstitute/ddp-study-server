package org.broadinstitute.ddp.event.pubsubtask.impl;

import static org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile.UpdateProfileProcessor.TASK_TYPE__UPDATE_PROFILE;


import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskProcessorFactory;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskProcessorFactoryAbstract;
import org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile.UpdateProfileProcessor;

/**
 * Default implementation of {@link PubSubTaskProcessorFactory}.
 */
public class PubSubTaskProcessorFactoryImpl extends PubSubTaskProcessorFactoryAbstract {

    @Override
    protected void registerPubSubTaskProcessors() {
        registerPubSubTaskProcessors(
                TASK_TYPE__UPDATE_PROFILE,
                new UpdateProfileProcessor(),
                null
        );
    }
}
