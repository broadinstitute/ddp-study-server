package org.broadinstitute.dsm.model.defaultvalues;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dao.bookmark.BookmarkDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.bookmark.BookmarkDto;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.model.ddp.DDPActivityConstants;
import org.broadinstitute.dsm.model.elastic.ESActivities;
import org.broadinstitute.dsm.model.elastic.ESProfile;
import org.broadinstitute.dsm.model.settings.field.FieldSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ATDefaultValues extends BasicDefaultDataMaker {

    private static final Logger logger = LoggerFactory.getLogger(ATDefaultValues.class);

    private static final String GENOME_STUDY_FIELD_TYPE = "AT_GROUP_GENOME_STUDY";
    private static final String GENOME_STUDY_CPT_ID = "GENOME_STUDY_CPT_ID";
    private static final String PREFIX = "DDP_ATCP_";
    public static final String EXIT_STATUS = "EXITSTATUS";

    public static final String AT_PARTICIPANT_EXIT = "AT_PARTICIPANT_EXIT";
    public static final String AT_GENOMIC_ID = "at_genomic_id";
    public static final String ACTIVITY_CODE_PREQUAL = "PREQUAL";
    public static final String PREQUAL_SELF_DESCRIBE = "PREQUAL_SELF_DESCRIBE";
    public static final String QUESTION_ANSWER = "answer";
    public static final String SELF_DESCRIBE_CHILD_DIAGNOSED = "CHILD_DIAGNOSED";
    public static final String SELF_DESCRIBE_DIAGNOSED = "DIAGNOSED";
    private Dao dataAccess;

    @Override
    protected boolean setDefaultData() {

        if (isParticipantDataInES())
            return false;

        if (isSelfOrDependentParticipant()) {
            return insertGenomicIdForParticipant()
                    && insertExitStatusForParticipant();
        } else {
            //in case if 3rd registration option is chosen in prequalifier of ATCP
            //which is just stay inform registration
            return true;
        }
    }

    private boolean isParticipantDataInES() {
        logger.info("Participant does not have profile and activities in ES yet...");
        return elasticSearchParticipantDto.getProfile().isEmpty() && elasticSearchParticipantDto.getActivities().isEmpty();
    }


    boolean isSelfOrDependentParticipant() {
        if (elasticSearchParticipantDto.getActivities().isEmpty()) return false;
        return elasticSearchParticipantDto.getActivities().stream()
                .anyMatch(this::isPrequalAndSelfOrDependent);
    }

    private boolean isPrequalAndSelfOrDependent(ESActivities activity) {
        return ACTIVITY_CODE_PREQUAL.equals(activity.getActivityCode()) &&
                (activity.getQuestionsAnswers().stream()
                        .filter(anwers -> PREQUAL_SELF_DESCRIBE.equals(anwers.get(DDPActivityConstants.DDP_ACTIVITY_STABLE_ID)))
                        .anyMatch(answers -> ((List) answers.get(QUESTION_ANSWER)).stream().anyMatch(answer -> SELF_DESCRIBE_CHILD_DIAGNOSED.equals(answer) ||
                                SELF_DESCRIBE_DIAGNOSED.equals(answer))));
    }

    private boolean insertGenomicIdForParticipant() {
        ESProfile esProfile = elasticSearchParticipantDto.getProfile().orElseThrow();
        String ddpParticipantId = esProfile.getGuid();
        String hruid = esProfile.getHruid();
        return insertParticipantData(Map.of(GENOME_STUDY_CPT_ID, PREFIX.concat(getGenomicIdValue(hruid))), ddpParticipantId,
                GENOME_STUDY_FIELD_TYPE);
    }

    private String getGenomicIdValue(String hruid) {
        this.setDataAccess(new BookmarkDao());
        BookmarkDao dataAccess = (BookmarkDao) this.dataAccess;
        Optional<BookmarkDto> maybeGenomicId = dataAccess.getBookmarkByInstance(AT_GENOMIC_ID);
        return maybeGenomicId
                .map(bookmarkDto -> {
                    dataAccess.updateBookmarkValueByBookmarkId(bookmarkDto.getBookmarkId(), bookmarkDto.getValue() + 1);
                    return String.valueOf(bookmarkDto.getValue());
                })
                .orElse(hruid);
    }

    private boolean insertExitStatusForParticipant() {
        String ddpParticipantId = elasticSearchParticipantDto.getProfile().orElseThrow().getGuid();
        String datstatExitReasonDefaultOption = getDefaultExitStatus();
        return insertParticipantData(Map.of(EXIT_STATUS, datstatExitReasonDefaultOption), ddpParticipantId, AT_PARTICIPANT_EXIT);
    }

    private String getDefaultExitStatus() {
        this.setDataAccess(FieldSettingsDao.of());
        Optional<FieldSettingsDto> fieldSettingByColumnNameAndInstanceId = ((FieldSettingsDao) dataAccess)
                .getFieldSettingByColumnNameAndInstanceId(Integer.parseInt(instance.getDdpInstanceId()), EXIT_STATUS);
        return fieldSettingByColumnNameAndInstanceId.
                map(fieldSettingsDto -> {
                    FieldSettings fieldSettings = new FieldSettings();
                    return fieldSettings.getDefaultValue(fieldSettingsDto.getPossibleValues());
                })
                .orElse(StringUtils.EMPTY);
    }

    private boolean insertParticipantData(Map<String, String> data, String ddpParticipantId, String fieldTypeId) {
        this.setDataAccess(new ParticipantDataDao());
        org.broadinstitute.dsm.model.participant.data.ParticipantData participantData = new org.broadinstitute.dsm.model.participant.data.ParticipantData(dataAccess);
        participantData.setData(ddpParticipantId, Integer.parseInt(instance.getDdpInstanceId()),
                fieldTypeId, data);
        try {
            participantData.insertParticipantData("SYSTEM");
            logger.info("values: " + data.keySet().stream().collect(Collectors.joining(", ", "[", "]")) + " were created for participant with id: " + ddpParticipantId + " at " +
                    GENOME_STUDY_FIELD_TYPE);
            return true;
        } catch (RuntimeException re) {
            return false;
        }
    }

    private void setDataAccess(Dao dao) {
        this.dataAccess = dao;
    }


}
