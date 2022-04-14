package org.broadinstitute.ddp.cache;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;

public interface DaoBuilder<T extends SqlObject> {
    T buildDao(Handle handle);
}
