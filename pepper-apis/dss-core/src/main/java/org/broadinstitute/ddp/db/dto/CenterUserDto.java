package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@SuperBuilder(toBuilder = true)
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class CenterUserDto {
    @ColumnName("center_user_id")
    long id;
    
    @ColumnName("center_id")
    long centerId;

    @ColumnName("user_id")
    long userId;
}
