package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class InstitutionDto {
    @ColumnName("institution_id")
    long institutionId;
    
    @ColumnName("institution_guid")
    String institutionGuid;
    
    @ColumnName("city_id")
    long cityId;
    
    @ColumnName("name")
    String name;
}
