package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class MedicalProviderDto {
    Long userMedicalProviderId;
    String userMedicalProviderGuid;
    long userId;
    long umbrellaStudyId;

    @ColumnName("institution_type_code")
    InstitutionType institutionType;

    String institutionName;
    String physicianName;
    String city;
    String state;
    String country;
    String postalCode;
    String phone;
    String legacyGuid;
    String street;

    public boolean isBlank() {
        return StringUtils.isBlank(physicianName)
                && StringUtils.isBlank(institutionName)
                && StringUtils.isBlank(city)
                && StringUtils.isBlank(state);
    }
}
