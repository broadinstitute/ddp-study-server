package org.broadinstitute.ddp.service.actvityinstancebuilder.context;

import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;

/**
 * Steps of {@link ActivityInstance} building with {@link ActivityInstanceFromDefinitionBuilder}
 */
public enum AIBuildStep {
    INIT,
    CHECK_PARAMS,
        READ_FORM_INSTANCE,
        READ_ACTIVITY_DEF,
        CREATE_RENDERER_CONTEXT,
    START_BUILD,
        BUILD_FORM_INSTANCE,
        BUILD_FORM_CHILDREN,
        RENDER_FORM_TITLES,
        RENDER_CONTENT,
        UPDATE_BLOCK_STATUSES,
        SET_DISPLAY_NUMBERS,
        POPULATE_SNAPSHOTTED_ADDRESS,
    END_BUILD
}
