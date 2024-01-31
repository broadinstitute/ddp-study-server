package org.broadinstitute.dsm.util;

import static org.broadinstitute.dsm.model.defaultvalues.ATDefaultValues.AT_PARTICIPANT_EXIT;
import static org.broadinstitute.dsm.model.defaultvalues.ATDefaultValues.EXIT_STATUS;

import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;

public class FieldSettingsTestUtil {

    /**
     * Creates a radio button field setting
     * @return fieldSettingsId
     */
    public static int createRadioFieldSetting(String fieldType, String columnName, String possibeValues,
                                              int ddpInstanceId) {
        FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();
        FieldSettingsDto.Builder builder = new FieldSettingsDto.Builder(ddpInstanceId);
        FieldSettingsDto fieldSettings = builder.withFieldType(fieldType)
                .withColumnName(columnName)
                .withDisplayType("RADIO")
                .withPossibleValues(possibeValues)
                .build();
        return fieldSettingsDao.create(fieldSettings);
    }

    public static int createExitStatusFieldSetting(int ddpInstanceId) {
        return FieldSettingsTestUtil.createRadioFieldSetting(AT_PARTICIPANT_EXIT,
                EXIT_STATUS,
                "[{\"value\":\"0\",\"name\":\"Not Exited\",\"default\":true},"
                        + "{\"value\":\"1\",\"name\":\"Exited\"}]",
                ddpInstanceId);
    }

    public static void deleteFieldSettings(int fieldSettingsId) {
        FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();
        fieldSettingsDao.delete(fieldSettingsId);
    }
}
