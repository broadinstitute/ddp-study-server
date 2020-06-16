package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.cache.ModelChangeType;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.HandleCallback;
import org.jdbi.v3.core.HandleConsumer;
import org.jdbi.v3.sqlobject.SqlObject;

public class SQLObjectWrapper<T extends SqlObject> {
    protected final T target;

    public SQLObjectWrapper(Handle handle, Class<T> targetDaoI) {
        this.target = handle.attach(targetDaoI);
    }

    public Handle getHandle() {
        return target.getHandle();
    }

    public <R, X extends Exception> R withHandle(HandleCallback<R, X> callback) throws X {
        return target.withHandle(callback);
    }

    public <X extends Exception> void useHandle(HandleConsumer<X> consumer) throws X {
        target.useHandle(consumer);
    }

    protected void notifyModelUpdated(ModelChangeType changeType, long id) {
        CacheService.getInstance().modelUpdated(changeType, getHandle(), id);
    }
}
