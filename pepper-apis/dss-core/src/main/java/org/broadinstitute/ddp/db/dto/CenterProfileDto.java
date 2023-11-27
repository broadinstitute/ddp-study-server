package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class CenterProfileDto {
    @ColumnName("center_id")
    long id;
    
    @ColumnName("name")
    String name;

    @ColumnName("address")
    String address;
}
