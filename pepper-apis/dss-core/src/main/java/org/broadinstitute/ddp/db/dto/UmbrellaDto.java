package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import java.io.Serializable;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class UmbrellaDto implements Serializable {
    @ColumnName("umbrella_id")
    long id;

    @ColumnName("umbrella_name")
    String name;

    @ColumnName("umbrella_guid")
    String guid;
}
