package org.broadinstitute.ddp.model.copy;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class CopyLocation {

    private long id;
    private CopyLocationType type;

    @JdbiConstructor
    public CopyLocation(@ColumnName("copy_location_id") long id, @ColumnName("copy_location_type") CopyLocationType type) {
        this.id = id;
        this.type = type;
    }

    public CopyLocation(CopyLocationType type) {
        this.type = type;
    }

    public long getId() {
        return id;
    }

    public CopyLocationType getType() {
        return type;
    }
}
