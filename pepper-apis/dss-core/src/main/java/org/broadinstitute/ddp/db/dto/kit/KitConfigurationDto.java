package org.broadinstitute.ddp.db.dto.kit;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class KitConfigurationDto {
    @ColumnName("kit_configuration_id")
    long id;

    @ColumnName("study_id")
    long studyId;

    @ColumnName("study_guid")
    String studyGuid;

    @ColumnName("number_of_kits")
    long numberOfKits;

    @ColumnName("kit_type_id")
    long kitTypeId;

    @ColumnName("needs_approval")
    boolean approvalNeeded;
}
