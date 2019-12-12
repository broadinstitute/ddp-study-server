package org.broadinstitute.ddp.model.event;

import org.jdbi.v3.core.Handle;

public interface ValueSetter<T> {
    Class<T> getValueType();

    boolean setValue(T newValue, long participantId, long operatorId, Handle handle);
}
