package org.broadinstitute.ddp.db.dto.pdf;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class PhysicianInstitutionTemplateDto {
    @ColumnName("template_id")
    long pdfBaseTemplateId;

    @ColumnName("physician_name_placeholder")
    String physicianNamePlaceholder;

    @ColumnName("institution_name_placeholder")
    String institutionNamePlaceholder;

    @ColumnName("city_placeholder")
    String cityPlaceholder;

    @ColumnName("state_placeholder")
    String statePlaceholder;

    @ColumnName("street_placeholder")
    String streetPlaceholder;

    @ColumnName("zip_placeholder")
    String zipPlaceholder;

    @ColumnName("phone_placeholder")
    String phonePlaceholder;

    @ColumnName("institution_type")
    InstitutionType institutionType;
}
