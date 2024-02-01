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
    public static int createRadioFieldSetting(FieldSettingsDto.Builder builder) {
        FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();
        FieldSettingsDto fieldSettings = builder
                .withDisplayType("RADIO")
                .build();
        return fieldSettingsDao.create(fieldSettings);
    }

    public static int createExitStatusFieldSetting(int ddpInstanceId) {
        FieldSettingsDto.Builder builder = new FieldSettingsDto.Builder(ddpInstanceId);
        builder.withFieldType(AT_PARTICIPANT_EXIT)
                .withColumnName(EXIT_STATUS)
                .withPossibleValues("[{\"value\":\"0\",\"name\":\"Not Exited\",\"default\":true},"
                + "{\"value\":\"1\",\"name\":\"Exited\"}]");
        return FieldSettingsTestUtil.createRadioFieldSetting(builder);
    }

    public static int createRegistrationStatusFieldSetting(String fieldTypeId, int ddpInstanceId) {
        String values = "[{\"value\":\"NotRegistered\",\"name\":\"Not Registered\"},"
                + "{\"value\":\"Registered\",\"name\":\"Registered\"},"
                + "{\"value\":\"Consented\",\"name\":\"Consented\"},"
                + "{\"value\":\"SubmittedEnrollment\",\"name\":\"Submitted Enrollment/Pending Confirmation\"}]";
        String actions = "[{\"name\":\"REGISTRATION_STATUS\",\"type\":\"ELASTIC_EXPORT.workflows\"}]";

        FieldSettingsDto.Builder builder = new FieldSettingsDto.Builder(ddpInstanceId);
        builder.withFieldType(fieldTypeId)
                .withColumnName("REGISTRATION_STATUS")
                .withPossibleValues(values)
                .withActions(actions);
        return FieldSettingsTestUtil.createRadioFieldSetting(builder);
    }

    public static void deleteFieldSettings(int fieldSettingsId) {
        FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();
        fieldSettingsDao.delete(fieldSettingsId);
    }
}
