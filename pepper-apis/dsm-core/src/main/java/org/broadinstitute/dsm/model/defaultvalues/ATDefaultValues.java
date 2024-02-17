package org.broadinstitute.dsm.model.defaultvalues;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.bookmark.BookmarkDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.bookmark.BookmarkDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.exception.ESMissingParticipantDataException;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.settings.field.FieldSettings;
import org.broadinstitute.dsm.pubsub.WorkflowStatusUpdate;

@Slf4j
public class ATDefaultValues extends BasicDefaultDataMaker {
    protected static final String AT_GENOMIC_ID = "at_genomic_id";
    protected static final String ACTIVITY_CODE_REGISTRATION = "REGISTRATION";
    protected static final String COMPLETE = "COMPLETE";
    public static final String GENOME_STUDY_CPT_ID = "GENOME_STUDY_CPT_ID";
    public static final String GENOMIC_ID_PREFIX = "DDP_ATCP_";
    public static final String AT_PARTICIPANT_EXIT = "AT_PARTICIPANT_EXIT";
    public static final String GENOME_STUDY_FIELD_TYPE = "AT_GROUP_GENOME_STUDY";
    public static final String EXIT_STATUS = "EXITSTATUS";

    /**
     * Inserts default data for a participant in the AT study: a genomic id and an exit status
     *
     * @return true if data was actually inserted
     */
    @Override
    protected boolean setDefaultData(String ddpParticipantId) {
        // expecting ptp has a profile and has completed the enrollment activity
        if (elasticSearchParticipantDto.getProfile().isEmpty()
                || elasticSearchParticipantDto.getActivities().isEmpty()) {
            throw new ESMissingParticipantDataException(String.format("Participant %s does not yet have profile and "
                    + "activities in ES", elasticSearchParticipantDto.getQueriedParticipantId()));
        }

        Profile profile = elasticSearchParticipantDto.getProfile().orElseThrow();
        try {
            if (!createGenomicId(ddpParticipantId, profile.getHruid())) {
                return false;
            }
            WorkflowStatusUpdate.updateEsParticipantData(ddpParticipantId, instance);
        } catch (Exception e) {
            throw new DsmInternalError("Error setting AT default data for participant " + ddpParticipantId, e);
        }
        return true;
    }

    protected synchronized boolean createGenomicId(String ddpParticipantId, String hruid) {
        List<ParticipantData> participantDataList =
                participantDataDao.getParticipantDataByParticipantId(ddpParticipantId);

        boolean hasGenomeId = participantDataList.stream().anyMatch(
                participantDataDto -> ATDefaultValues.GENOME_STUDY_FIELD_TYPE.equals(
                        participantDataDto.getRequiredFieldTypeId()));
        if (!hasGenomeId) {
            insertGenomicIdForParticipant(ddpParticipantId, hruid, instanceId);
        }

        boolean hasExitStatus = participantDataList.stream().anyMatch(
                participantDataDto -> ATDefaultValues.AT_PARTICIPANT_EXIT.equals(
                        participantDataDto.getRequiredFieldTypeId()));
        if (!hasExitStatus) {
            insertExitStatusForParticipant(ddpParticipantId, instanceId);
        }

        if (hasGenomeId && hasExitStatus) {
            log.info("Participant {} already has AT default data", ddpParticipantId);
            return false;
        }
        return true;
    }

    protected boolean isParticipantRegistrationComplete() {
        return elasticSearchParticipantDto.getActivities().stream().anyMatch(this::isRegistrationComplete);
    }

    private boolean isRegistrationComplete(Activities activity) {
        return ACTIVITY_CODE_REGISTRATION.equals(activity.getActivityCode()) && COMPLETE.equals(activity.getStatus());
    }

    /**
     * Insert a genomic id for a participant in the AT study
     * Note: this updates ParticipantData in the DB, but does not update ES
     *
     * @param hruid hruid of the participant, per the ES profile
     */
    public static void insertGenomicIdForParticipant(String ddpParticipantId, String hruid, int instanceId) {
        insertParticipantData(GENOME_STUDY_FIELD_TYPE,
                Map.of(GENOME_STUDY_CPT_ID, GENOMIC_ID_PREFIX.concat(getGenomicIdValue(hruid))),
                ddpParticipantId, instanceId);
    }

    private static String getGenomicIdValue(String hruid) {
        BookmarkDao bookmarkDao = new BookmarkDao();
        Optional<BookmarkDto> maybeGenomicId = bookmarkDao.getBookmarkByInstance(AT_GENOMIC_ID);
        return maybeGenomicId.map(bookmarkDto -> {
            bookmarkDao.updateBookmarkValueByBookmarkId(bookmarkDto.getBookmarkId(), bookmarkDto.getValue() + 1);
            return String.valueOf(bookmarkDto.getValue());
        }).orElse(hruid);
    }

    /**
     * Insert default exit status for a participant in the AT study
     * Note: this updates ParticipantData in the DB, but does not update ES
     */
    public static void insertExitStatusForParticipant(String ddpParticipantId, int instanceId) {
        String defaultValue = getDefaultExitStatus(instanceId);
        if (StringUtils.isBlank(defaultValue)) {
            throw new DsmInternalError("No default exit status found for AT study");
        }
        insertParticipantData(AT_PARTICIPANT_EXIT, Map.of(EXIT_STATUS, defaultValue), ddpParticipantId, instanceId);
    }

    private static String getDefaultExitStatus(int instanceId) {
        FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();
        Optional<FieldSettingsDto> fieldSettingByColumnNameAndInstanceId =
                fieldSettingsDao.getFieldSettingByColumnNameAndInstanceId(instanceId, EXIT_STATUS);
        return fieldSettingByColumnNameAndInstanceId.map(fieldSettingsDto -> {
            FieldSettings fieldSettings = new FieldSettings();
            return fieldSettings.getDefaultValue(fieldSettingsDto.getPossibleValues());
        }).orElse(StringUtils.EMPTY);
    }

    private static void insertParticipantData(String fieldTypeId, Map<String, String> data, String ddpParticipantId,
                                              int instanceId) {
        org.broadinstitute.dsm.model.participant.data.ParticipantData participantData =
                new org.broadinstitute.dsm.model.participant.data.ParticipantData(participantDataDao);
        participantData.setData(ddpParticipantId, instanceId, fieldTypeId, data);
        participantData.insertParticipantData("SYSTEM");
        log.info("{} record created for participant {}", fieldTypeId, ddpParticipantId);
    }
}
