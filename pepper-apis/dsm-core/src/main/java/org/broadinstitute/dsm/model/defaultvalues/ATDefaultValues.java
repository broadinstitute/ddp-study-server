package org.broadinstitute.dsm.model.defaultvalues;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dao.bookmark.BookmarkDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.bookmark.BookmarkDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.ObjectTransformer;
import org.broadinstitute.dsm.model.settings.field.FieldSettings;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ATDefaultValues extends BasicDefaultDataMaker {

    private static final Logger logger = LoggerFactory.getLogger(ATDefaultValues.class);

    public static final String EXIT_STATUS = "EXITSTATUS";
    public static final String AT_PARTICIPANT_EXIT = "AT_PARTICIPANT_EXIT";
    public static final String AT_GENOMIC_ID = "at_genomic_id";
    public static final String ACTIVITY_CODE_REGISTRATION = "REGISTRATION";
    public static final String COMPLETE = "COMPLETE";
    public static final String GENOME_STUDY_FIELD_TYPE = "AT_GROUP_GENOME_STUDY";
    private static final String GENOME_STUDY_CPT_ID = "GENOME_STUDY_CPT_ID";
    private static final String PREFIX = "DDP_ATCP_";
    private Dao dataAccess;

    @Override
    protected boolean setDefaultData() {
        if (isParticipantDataNotInES()) {
            logger.info("Participant does not have profile and activities in ES yet...");
            return false;
        }

        if (isParticipantRegistrationComplete()) {
            boolean inserted = insertExitStatusForParticipant() && insertGenomicIdForParticipant();

            try {
                String ddpParticipantId = elasticSearchParticipantDto.getProfile().orElseThrow().getGuid();
                ObjectTransformer objectTransformer = new ObjectTransformer(instance.getName());
                this.setDataAccess(new ParticipantDataDao());
                List<ParticipantData> participantDataList =
                        ((ParticipantDataDao) dataAccess).getParticipantDataByParticipantId(ddpParticipantId);
                List<Map<String, Object>> transformedList =
                        objectTransformer.transformObjectCollectionToCollectionMap((List) participantDataList);
                ElasticSearchUtil.updateRequest(ddpParticipantId, instance.getParticipantIndexES(), new HashMap<>(
                        Map.of(ESObjectConstants.DSM, new HashMap<>(Map.of(ESObjectConstants.PARTICIPANT_DATA, transformedList)))));

            } catch (Exception e) {
                logger.info("UpdateRequest for participantData failed" + e.getMessage());
            }
            return inserted;
        } else {
            //in case if 3rd registration option is chosen in prequalifier of ATCP
            //which is just stay inform registration
            return true;
        }
    }

    private boolean isParticipantDataNotInES() {
        return elasticSearchParticipantDto.getProfile().isEmpty() && elasticSearchParticipantDto.getActivities().isEmpty();
    }


    boolean isParticipantRegistrationComplete() {
        return elasticSearchParticipantDto.getActivities().stream().anyMatch(this::isRegistrationComplete);
    }

    private boolean isRegistrationComplete(Activities activity) {
        return ACTIVITY_CODE_REGISTRATION.equals(activity.getActivityCode()) && COMPLETE.equals(activity.getStatus());
    }

    private boolean insertGenomicIdForParticipant() {
        Profile esProfile = elasticSearchParticipantDto.getProfile().orElseThrow();
        String ddpParticipantId = esProfile.getGuid();
        String hruid = esProfile.getHruid();
        return insertParticipantData(Map.of(GENOME_STUDY_CPT_ID, PREFIX.concat(getGenomicIdValue(hruid))), ddpParticipantId,
                GENOME_STUDY_FIELD_TYPE);
    }

    private String getGenomicIdValue(String hruid) {
        this.setDataAccess(new BookmarkDao());
        Optional<BookmarkDto> maybeGenomicId = ((BookmarkDao) dataAccess).getBookmarkByInstance(AT_GENOMIC_ID);
        return maybeGenomicId.map(bookmarkDto -> {
            ((BookmarkDao) dataAccess).updateBookmarkValueByBookmarkId(bookmarkDto.getBookmarkId(), bookmarkDto.getValue() + 1);
            return String.valueOf(bookmarkDto.getValue());
        }).orElse(hruid);
    }

    private boolean insertExitStatusForParticipant() {
        String ddpParticipantId = elasticSearchParticipantDto.getProfile().orElseThrow().getGuid();
        String datstatExitReasonDefaultOption = getDefaultExitStatus();
        return insertParticipantData(Map.of(EXIT_STATUS, datstatExitReasonDefaultOption), ddpParticipantId, AT_PARTICIPANT_EXIT);
    }

    private String getDefaultExitStatus() {
        this.setDataAccess(FieldSettingsDao.of());
        Optional<FieldSettingsDto> fieldSettingByColumnNameAndInstanceId =
                ((FieldSettingsDao) dataAccess).getFieldSettingByColumnNameAndInstanceId(Integer.parseInt(instance.getDdpInstanceId()),
                        EXIT_STATUS);
        return fieldSettingByColumnNameAndInstanceId.map(fieldSettingsDto -> {
            FieldSettings fieldSettings = new FieldSettings();
            return fieldSettings.getDefaultValue(fieldSettingsDto.getPossibleValues());
        }).orElse(StringUtils.EMPTY);
    }

    private boolean insertParticipantData(Map<String, String> data, String ddpParticipantId, String fieldTypeId) {
        this.setDataAccess(new ParticipantDataDao());
        org.broadinstitute.dsm.model.participant.data.ParticipantData participantData =
                new org.broadinstitute.dsm.model.participant.data.ParticipantData(dataAccess);
        participantData.setData(ddpParticipantId, Integer.parseInt(instance.getDdpInstanceId()), fieldTypeId, data);
        try {
            participantData.insertParticipantData("SYSTEM");
            logger.info("values: " + data.keySet().stream().collect(Collectors.joining(", ", "[", "]"))
                    + " were created at PARTICIPANT_REGISTERED pubsub task for participant with id: " + ddpParticipantId + " at "
                    + fieldTypeId);
            return true;
        } catch (RuntimeException re) {
            return false;
        }
    }

    private void setDataAccess(Dao dao) {
        this.dataAccess = dao;
    }

}
