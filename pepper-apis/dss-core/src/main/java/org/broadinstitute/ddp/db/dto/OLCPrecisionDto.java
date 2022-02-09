package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.model.address.OLCPrecision;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class OLCPrecisionDto {
    private long id;
    private OLCPrecision olcPrecision;

    @JdbiConstructor
    public OLCPrecisionDto(@ColumnName("olc_precision_id") long id,
                           @ColumnName("olc_precision_code") OLCPrecision olcPrecision) {
        this.id = id;
        this.olcPrecision = olcPrecision;
    }

    public long getId() {
        return id;
    }

    public OLCPrecision getOlcPrecision() {
        return olcPrecision;
    }
}
