package org.broadinstitute.ddp.cache;

import org.jdbi.v3.core.Handle;

public interface IdToCacheKeyMapper<K> {
    K mapToKey(Long id, Handle handle);
}
