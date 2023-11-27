package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.model.address.OLCPrecision;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class OLCPrecisionDto {
    @ColumnName("olc_precision_id")
    long id;

    @ColumnName("olc_precision_code")
    OLCPrecision olcPrecision;
}
