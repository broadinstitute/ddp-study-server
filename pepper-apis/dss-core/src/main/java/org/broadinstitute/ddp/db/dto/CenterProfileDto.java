package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@SuperBuilder(toBuilder = true)
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class CenterProfileDto {
    @ColumnName("center_id")
    long id;

    @ColumnName("city_id")
    long cityId;

    @ColumnName("primary_contact_id")
    long primaryContactId;
    
    @ColumnName("name")
    String name;

    @ColumnName("address1")
    String address1;

    @ColumnName("address2")
    String address2;

}
