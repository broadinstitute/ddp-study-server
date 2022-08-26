package org.broadinstitute.dsm.model.defaultvalues;

import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantRecordDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.model.participant.data.ParticipantRecord;
import org.broadinstitute.dsm.model.settings.field.FieldSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SingularDefaultValues extends BasicDefaultDataMaker {

    private static final Logger logger = LoggerFactory.getLogger(SingularDefaultValues.class);

    public static final String COLUMN_NAME = "SINGULAR_ENROLLMENT_STATUS";
    public static final String COLUMN_NAME_CAMEL_CASE = "singularEnrollmentStatus";

    private Dao dataAccess;

    @Override
    protected boolean setDefaultData() {
        if (isParticipantDataInES()) {
            return false;
        }
        return insertDefaultEnrollmentStatusForParticipant();
    }

    private boolean isParticipantDataInES() {
        logger.info("Participant does not have profile and activities in ES yet...");
        return elasticSearchParticipantDto.getProfile().isEmpty() && elasticSearchParticipantDto.getActivities().isEmpty();
    }

    private boolean insertDefaultEnrollmentStatusForParticipant() {
        String ddpParticipantId = elasticSearchParticipantDto.getProfile().orElseThrow().getGuid();
        String enrollmentStatusDefaultOption = getEnrollmentStatus();
        return insertParticipantData(Map.of(COLUMN_NAME_CAMEL_CASE, enrollmentStatusDefaultOption), ddpParticipantId);
    }

    private String getEnrollmentStatus() {
        this.setDataAccess(FieldSettingsDao.of());
        Optional<FieldSettingsDto> fieldSettingByColumnNameAndInstanceId =
                ((FieldSettingsDao) dataAccess).getFieldSettingByColumnNameAndInstanceId(Integer.parseInt(instance.getDdpInstanceId()),
                        COLUMN_NAME);
        return fieldSettingByColumnNameAndInstanceId.map(fieldSettingsDto -> {
            FieldSettings fieldSettings = new FieldSettings();
            return fieldSettings.getDefaultValue(fieldSettingsDto.getPossibleValues());
        }).orElse(StringUtils.EMPTY);
    }

    private boolean insertParticipantData(Map<String, String> data, String ddpParticipantId) {
        this.setDataAccess(ParticipantRecordDao.of());

        ParticipantRecord participantRecord = new ParticipantRecord(ddpParticipantId, Integer.parseInt(instance.getDdpInstanceId()),
                (ParticipantRecordDao) dataAccess);
        int newParticipantId = participantRecord.createNewParticipantRecord();
        return participantRecord.insertDefaultValues(data, newParticipantId, instance);
    }

    private void setDataAccess(Dao dao) {
        this.dataAccess = dao;
    }

//     this.setDataAccess(new ParticipantDataDao());
//    org.broadinstitute.dsm.model.participant.data.ParticipantData participantData =
//            new org.broadinstitute.dsm.model.participant.data.ParticipantData(dataAccess);
//        participantData.setData(ddpParticipantId, Integer.parseInt(instance.getDdpInstanceId()), fieldTypeId, data);
//        try {
//        participantData.insertParticipantData("SYSTEM");
//        logger.info("values: " + data.keySet().stream().collect(Collectors.joining(", ", "[", "]"))
//                + " were created for participant with id: " + ddpParticipantId + " at " + FIELD_TYPE);
//        return true;
//    } catch (RuntimeException re) {
//        return false;
//    }

}
