package org.broadinstitute.dsm.model.elastic.sort;

import java.util.Optional;

import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;

public class MockFieldSettingsDao extends FieldSettingsDao {

    public String refSourceValues;
    public String refSourceDetails;

    @Override
    public Optional<FieldSettingsDto> getFieldSettingsByFieldTypeAndColumnName(String fieldType, String columnName) {
        if (fieldType.equals("RGP_MEDICAL_RECORDS_GROUP")) {
            FieldSettingsDto fieldSettingsDto = new FieldSettingsDto.Builder(0)
                    .withPossibleValues(refSourceValues).withDetails(refSourceDetails).build();
            return Optional.of(fieldSettingsDto);
        }

        String possibleValues = "[{\"value\":\"REGISTRATION.REGISTRATION_GENDER\"}]";
        FieldSettingsDto fieldSettingsDto = new FieldSettingsDto.Builder(0).withPossibleValues(possibleValues).build();
        return Optional.of(fieldSettingsDto);
    }
}
