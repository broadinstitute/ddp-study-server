package org.broadinstitute.ddp.cache;

import java.util.Set;

import org.jdbi.v3.core.Handle;

public interface IdToCacheKeyCollectionMapper<K> {
    Set<K> mapToKeys(Long id, Handle handle);
}
