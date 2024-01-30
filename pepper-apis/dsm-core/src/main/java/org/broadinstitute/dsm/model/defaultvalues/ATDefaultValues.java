package org.broadinstitute.dsm.model.defaultvalues;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.bookmark.BookmarkDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.bookmark.BookmarkDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.exception.ESMissingParticipantDataException;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.ObjectTransformer;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.settings.field.FieldSettings;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

@Slf4j
public class ATDefaultValues extends BasicDefaultDataMaker {
    protected static final String EXIT_STATUS = "EXITSTATUS";
    public static final String AT_PARTICIPANT_EXIT = "AT_PARTICIPANT_EXIT";
    protected static final String AT_GENOMIC_ID = "at_genomic_id";
    protected static final String ACTIVITY_CODE_REGISTRATION = "REGISTRATION";
    protected static final String COMPLETE = "COMPLETE";
    public static final String GENOME_STUDY_FIELD_TYPE = "AT_GROUP_GENOME_STUDY";
    protected static final String GENOME_STUDY_CPT_ID = "GENOME_STUDY_CPT_ID";
    protected static final String GENOMIC_ID_PREFIX = "DDP_ATCP_";

    /**
     * Inserts default data for a participant in the AT study: a genomic id and an exit status
     *
     * @return true if data was actually inserted
     */
    @Override
    protected boolean setDefaultData() {
        // expecting ptp has a profile and has completed the enrollment activity
        if (elasticSearchParticipantDto.getProfile().isEmpty()
                || elasticSearchParticipantDto.getActivities().isEmpty()) {
            throw new ESMissingParticipantDataException(String.format("Participant %s does not yet have profile and "
                    + "activities in ES", elasticSearchParticipantDto.getQueriedParticipantId()));
        }

        Profile profile = elasticSearchParticipantDto.getProfile().orElseThrow();
        String ddpParticipantId = profile.getGuid();
        try {
            List<ParticipantData> participantDataList =
                    participantDataDao.getParticipantDataByParticipantId(ddpParticipantId);

            boolean hasGenomeId = participantDataList.stream().anyMatch(
                    participantDataDto -> ATDefaultValues.GENOME_STUDY_FIELD_TYPE.equals(
                            participantDataDto.getRequiredFieldTypeId()));
            if (!hasGenomeId) {
                insertGenomicIdForParticipant(ddpParticipantId, profile);
            }

            boolean hasExitStatus = participantDataList.stream().anyMatch(
                    participantDataDto -> ATDefaultValues.AT_PARTICIPANT_EXIT.equals(
                            participantDataDto.getRequiredFieldTypeId()));
            if (!hasExitStatus) {
                insertExitStatusForParticipant(ddpParticipantId);
            }

            if (hasGenomeId && hasExitStatus) {
                log.info("Participant {} already has AT default data", ddpParticipantId);
                return false;
            }

            updateEsParticipantData(ddpParticipantId, participantDataList, instance);
        } catch (Exception e) {
            throw new DsmInternalError("Error setting AT default data for participant " + ddpParticipantId, e);
        }
        return true;
    }

    protected boolean isParticipantRegistrationComplete() {
        return elasticSearchParticipantDto.getActivities().stream().anyMatch(this::isRegistrationComplete);
    }

    private boolean isRegistrationComplete(Activities activity) {
        return ACTIVITY_CODE_REGISTRATION.equals(activity.getActivityCode()) && COMPLETE.equals(activity.getStatus());
    }

    private void insertGenomicIdForParticipant(String ddpParticipantId, Profile esProfile) {
        String hruid = esProfile.getHruid();
        insertParticipantData(GENOME_STUDY_FIELD_TYPE,
                Map.of(GENOME_STUDY_CPT_ID, GENOMIC_ID_PREFIX.concat(getGenomicIdValue(hruid))), ddpParticipantId);
    }

    private String getGenomicIdValue(String hruid) {
        BookmarkDao bookmarkDao = new BookmarkDao();
        Optional<BookmarkDto> maybeGenomicId = bookmarkDao.getBookmarkByInstance(AT_GENOMIC_ID);
        return maybeGenomicId.map(bookmarkDto -> {
            bookmarkDao.updateBookmarkValueByBookmarkId(bookmarkDto.getBookmarkId(), bookmarkDto.getValue() + 1);
            return String.valueOf(bookmarkDto.getValue());
        }).orElse(hruid);
    }

    private void insertExitStatusForParticipant(String ddpParticipantId) {
        String defaultValue = getDefaultExitStatus();
        if (StringUtils.isBlank(defaultValue)) {
            throw new DsmInternalError("No default exit status found for AT study");
        }
        insertParticipantData(AT_PARTICIPANT_EXIT, Map.of(EXIT_STATUS, defaultValue), ddpParticipantId);
    }

    private String getDefaultExitStatus() {
        FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();
        Optional<FieldSettingsDto> fieldSettingByColumnNameAndInstanceId =
                fieldSettingsDao.getFieldSettingByColumnNameAndInstanceId(instanceId, EXIT_STATUS);
        return fieldSettingByColumnNameAndInstanceId.map(fieldSettingsDto -> {
            FieldSettings fieldSettings = new FieldSettings();
            return fieldSettings.getDefaultValue(fieldSettingsDto.getPossibleValues());
        }).orElse(StringUtils.EMPTY);
    }

    private void insertParticipantData(String fieldTypeId, Map<String, String> data, String ddpParticipantId) {
        org.broadinstitute.dsm.model.participant.data.ParticipantData participantData =
                new org.broadinstitute.dsm.model.participant.data.ParticipantData(participantDataDao);
        participantData.setData(ddpParticipantId, instanceId, fieldTypeId, data);
        participantData.insertParticipantData("SYSTEM");
        log.info("{} record created for participant {}", fieldTypeId, ddpParticipantId);
    }

    public static void updateEsParticipantData(String ddpParticipantId, Collection<ParticipantData> participantDataList,
                                               DDPInstance instance) {
        ObjectTransformer objectTransformer = new ObjectTransformer(instance.getName());
        List<Map<String, Object>> transformedList =
                objectTransformer.transformObjectCollectionToCollectionMap((List) participantDataList);
        ElasticSearchUtil.updateRequest(ddpParticipantId, instance.getParticipantIndexES(),
                new HashMap<>(Map.of(ESObjectConstants.DSM,
                        new HashMap<>(Map.of(ESObjectConstants.PARTICIPANT_DATA, transformedList)))));
        log.info("Updated DSM participantData in Elastic for {}", ddpParticipantId);
    }
}
