package org.broadinstitute.ddp.db.dao;

import javax.cache.Cache;

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.cache.ModelChangeType;
import org.broadinstitute.ddp.cache.NullCache;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.HandleCallback;
import org.jdbi.v3.core.HandleConsumer;
import org.jdbi.v3.sqlobject.SqlObject;
import org.redisson.api.RLocalCachedMap;

public class SQLObjectWrapper<T extends SqlObject> {
    protected final T delegate;

    public SQLObjectWrapper(Handle handle, Class<T> targetDaoI) {
        this.delegate = handle.attach(targetDaoI);
    }

    public Handle getHandle() {
        return delegate.getHandle();
    }

    public <R, X extends Exception> R withHandle(HandleCallback<R, X> callback) throws X {
        return delegate.withHandle(callback);
    }

    public <X extends Exception> void useHandle(HandleConsumer<X> consumer) throws X {
        delegate.useHandle(consumer);
    }

    protected void notifyModelUpdated(ModelChangeType changeType, long id) {
        CacheService.getInstance().modelUpdated(changeType, getHandle(), id);
    }

    protected boolean isNullCache(Cache cache) {
        return cache instanceof NullCache;
    }

    protected boolean isNullCache(RLocalCachedMap cache) {
        return cache == null;
    }
}
