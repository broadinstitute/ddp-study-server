package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.jdbi.v3.core.Handle;

public interface ValueSetter<T> {
    Class<T> getValueType();

    boolean setValue(T newValue, ActivityInstanceDto activityInstance, Handle handle);
}
