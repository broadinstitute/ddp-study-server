package org.broadinstitute.ddp.event.dsmtask.impl;

import static org.broadinstitute.ddp.event.dsmtask.impl.updateprofile.UpdateProfileProcessor.TASK_TYPE__UPDATE_PROFILE;


import org.broadinstitute.ddp.event.dsmtask.api.DsmTaskProcessorFactory;
import org.broadinstitute.ddp.event.dsmtask.api.DsmTaskProcessorFactoryAbstract;
import org.broadinstitute.ddp.event.dsmtask.impl.updateprofile.UpdateProfileData;
import org.broadinstitute.ddp.event.dsmtask.impl.updateprofile.UpdateProfileProcessor;

/**
 * Default implementation of {@link DsmTaskProcessorFactory}.
 */
public class DsmTaskProcessorFactoryImpl extends DsmTaskProcessorFactoryAbstract {

    @Override
    protected void registerDsmTaskProcessors() {
        registerDsmTaskProcessors(
                TASK_TYPE__UPDATE_PROFILE,
                new UpdateProfileProcessor(),
                UpdateProfileData.class
        );
    }
}
