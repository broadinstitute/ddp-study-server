package org.broadinstitute.dsm.service.participantdata;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.bookmark.BookmarkDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.bookmark.BookmarkDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.exception.ESMissingParticipantDataException;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.settings.field.FieldSettings;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;

@Slf4j
public class ATParticipantDataService {
    protected static final String AT_GENOMIC_ID = "at_genomic_id";
    protected static final String ACTIVITY_CODE_REGISTRATION = "REGISTRATION";
    protected static final String ACTIVITY_COMPLETE = "COMPLETE";
    public static final String GENOME_STUDY_CPT_ID = "GENOME_STUDY_CPT_ID";
    public static final String GENOMIC_ID_PREFIX = "DDP_ATCP_";
    public static final String AT_PARTICIPANT_EXIT = "AT_PARTICIPANT_EXIT";
    public static final String AT_GROUP_GENOME_STUDY = "AT_GROUP_GENOME_STUDY";
    public static final String EXIT_STATUS = "EXITSTATUS";
    protected static final ParticipantDataDao participantDataDao = new ParticipantDataDao();
    private static final ElasticSearchService elasticSearchService = new ElasticSearchService();

    public static boolean generateDefaultData(String studyGuid, String ddpParticipantId) {
        DDPInstance instance = DDPInstance.getDDPInstanceByGuid(studyGuid);
        if (instance == null) {
            throw new DSMBadRequestException("Invalid study GUID: " + studyGuid);
        }
        String esIndex = instance.getParticipantIndexES();
        if (StringUtils.isEmpty(esIndex)) {
            throw new DsmInternalError("No ES participant index for study " + studyGuid);
        }

        return createDefaultData(ddpParticipantId,
                elasticSearchService.getRequiredParticipantDocumentById(ddpParticipantId, esIndex), instance);
    }

    /**
     * Inserts default data for a participant in the AT study: a genomic id and an exit status
     *
     * @return true if data was actually inserted
     */
    protected static boolean createDefaultData(String ddpParticipantId, ElasticSearchParticipantDto esParticipantDto,
                                               DDPInstance ddpInstance) {
        // expecting ptp has a profile and has completed the enrollment activity
        if (esParticipantDto.getProfile().isEmpty() || esParticipantDto.getActivities().isEmpty()) {
            throw new ESMissingParticipantDataException(String.format("Participant %s does not yet have profile and "
                    + "activities in ES", ddpParticipantId));
        }

        log.info("Creating default data for participant {} and instance {}", ddpParticipantId, ddpInstance.getName());
        Profile profile = esParticipantDto.getProfile().orElseThrow();
        try {
            if (!createGenomicId(ddpParticipantId, profile.getHruid(), ddpInstance.getDdpInstanceIdAsInt())) {
                return false;
            }
            ParticipantDataService.updateEsParticipantData(ddpParticipantId, ddpInstance);
        } catch (Exception e) {
            throw new DsmInternalError("Error setting AT default data for participant " + ddpParticipantId, e);
        }
        return true;
    }

    protected static synchronized boolean createGenomicId(String ddpParticipantId, String hruid, int instanceId) {
        List<ParticipantData> participantDataList = participantDataDao.getParticipantData(ddpParticipantId);

        boolean hasGenomeId = participantDataList.stream().anyMatch(
                participantDataDto -> ATParticipantDataService.AT_GROUP_GENOME_STUDY.equals(
                        participantDataDto.getRequiredFieldTypeId()));
        if (!hasGenomeId) {
            insertGenomicIdForParticipant(ddpParticipantId, hruid, instanceId);
        }

        boolean hasExitStatus = participantDataList.stream().anyMatch(
                participantDataDto -> ATParticipantDataService.AT_PARTICIPANT_EXIT.equals(
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

    protected static boolean isParticipantRegistrationComplete(ElasticSearchParticipantDto esParticipantDto) {
        return esParticipantDto.getActivities().stream().anyMatch(ATParticipantDataService::isRegistrationComplete);
    }

    private static boolean isRegistrationComplete(Activities activity) {
        return ACTIVITY_CODE_REGISTRATION.equals(activity.getActivityCode()) && ACTIVITY_COMPLETE.equals(activity.getStatus());
    }

    /**
     * Insert a genomic id for a participant in the AT study
     * Note: this updates ParticipantData in the DB, but does not update ES
     *
     * @param hruid hruid of the participant, per the ES profile
     */
    public static void insertGenomicIdForParticipant(String ddpParticipantId, String hruid, int instanceId) {
        insertParticipantData(AT_GROUP_GENOME_STUDY,
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
