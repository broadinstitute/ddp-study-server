package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

@Value
@AllArgsConstructor
public class InstitutionSuggestionDto {
    @ColumnName("name")
    String name;
    
    @ColumnName("city")
    String city;
    
    @ColumnName("state")
    String state;

    @ColumnName("country")
    String country;
}
