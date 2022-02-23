package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class SendgridConfigurationDto {
    @ColumnName("umbrella_study_id")
    long umbrellaStudyId;
    
    @ColumnName("api_key")
    String apiKey;
    
    @ColumnName("from_name")
    String fromName;

    @ColumnName("from_email")
    String fromEmail;
    
    @ColumnName("default_salutation")
    String defaultSalutation;
}
