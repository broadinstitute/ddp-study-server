package org.broadinstitute.dsm.util.tools;

import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;

public class FieldSettingsTestUtil {

    /**
     * Creates a radio button field setting
     * @return fieldSettingsId
     */
    public static int createRadioFieldSettings(String fieldType, String columnName, String possibeValues,
                                                int ddpInstanceId) {
        FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();
        FieldSettingsDto.Builder builder = new FieldSettingsDto.Builder(ddpInstanceId);
        FieldSettingsDto fieldSettings = builder.withFieldType("AT_PARTICIPANT_EXIT")
                .withColumnName("EXITSTATUS")
                .withDisplayType("RADIO")
                .withColumnDisplay("Exit status:")
                .withPossibleValues("[{\"value\":\"0\",\"name\":\"Not Exited\",\"default\":true},"
                        + "{\"value\":\"1\",\"name\":\"Exited\"}]")
                .build();
        return fieldSettingsDao.create(fieldSettings);
    }

    public static void deleteFieldSettings(int fieldSettingsId) {
        FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();
        fieldSettingsDao.delete(fieldSettingsId);
    }
}
