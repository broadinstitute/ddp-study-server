package org.broadinstitute.dsm.service.onchistory;

import static org.broadinstitute.dsm.statics.ESObjectConstants.ONC_HISTORY;
import static org.broadinstitute.dsm.statics.ESObjectConstants.ONC_HISTORY_CREATED;
import static org.broadinstitute.dsm.statics.ESObjectConstants.ONC_HISTORY_ID;
import static org.broadinstitute.dsm.statics.ESObjectConstants.PARTICIPANT_ID;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.dsm.db.OncHistory;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDao;
import org.broadinstitute.dsm.db.dto.onchistory.OncHistoryDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.SystemUtil;

public class OncHistoryService {

    /**
     * Create or update OncHistory record, set the created date to now (if not already set), and update ES if needed
     */
    public static int createOrUpdateOncHistory(int participantId, String ddpParticipantId, String userId,
                                               OncHistoryElasticUpdater elasticUpdater) {
        OncHistoryDto oncHistoryDto;
        boolean updateEs = true;
        try {
            Optional<OncHistoryDto> res = OncHistoryDao.getByParticipantId(participantId);
            if (res.isEmpty()) {
                oncHistoryDto = new OncHistoryDto.Builder()
                        .withParticipantId(participantId)
                        .withChangedBy(userId)
                        .withLastChangedNow()
                        .withCreatedNow().build();
                OncHistoryDao oncHistoryDao = new OncHistoryDao();
                oncHistoryDto.setOncHistoryId(oncHistoryDao.create(oncHistoryDto));
            } else {
                oncHistoryDto = res.get();
                String createdDate = oncHistoryDto.getCreated();
                if (createdDate == null || createdDate.isEmpty()) {
                    createdDate = OncHistoryService.setCreatedNow(participantId, userId);
                    oncHistoryDto.setCreated(createdDate);
                } else {
                    updateEs = false;
                }
            }
        } catch (Exception e) {
            throw new DsmInternalError("Error updating onc history record for participant " + participantId, e);
        }

        if (updateEs) {
            updateEsOncHistory(oncHistoryDto, ddpParticipantId, elasticUpdater);
        }
        return oncHistoryDto.getOncHistoryId();
    }

    /**
     * Create an OncHistory record, but do not set the created date, and update ES
     */
    public static int createEmptyOncHistory(int participantId, String ddpParticipantId, String userId,
                                            OncHistoryElasticUpdater elasticUpdater) {
        OncHistoryDto oncHistoryDto;
        Optional<OncHistoryDto> res = OncHistoryDao.getByParticipantId(participantId);
        if (res.isPresent()) {
            throw new DsmInternalError("OncHistory record already exists for participant " + participantId);
        }
        oncHistoryDto = new OncHistoryDto.Builder()
                .withParticipantId(participantId)
                .withChangedBy(userId)
                .withLastChangedNow().build();
        OncHistoryDao oncHistoryDao = new OncHistoryDao();
        oncHistoryDto.setOncHistoryId(oncHistoryDao.create(oncHistoryDto));

        updateEsOncHistory(oncHistoryDto, ddpParticipantId, elasticUpdater);
        return oncHistoryDto.getOncHistoryId();
    }

    /**
     * Set oncHistory created date to now for participant iff current created date is null
     *
     * @return createdDate if created date was set, null if created date was already set or oncHistory record
     *              was not found
     */
    public static String setCreatedNow(int participantId, String userId) {
        String createdDate = SystemUtil.getDateFormatted(System.currentTimeMillis());
        return OncHistory.setOncHistoryCreated(participantId, createdDate, userId) ? createdDate : null;
    }

    private static void updateEsOncHistory(OncHistoryDto oncHistoryDto, String ddpParticipantId,
                                           OncHistoryElasticUpdater elasticUpdater) {
        Map<String, Object> oncHistory = new HashMap<>();
        oncHistory.put(PARTICIPANT_ID, oncHistoryDto.getParticipantId());
        oncHistory.put(ONC_HISTORY_ID, oncHistoryDto.getOncHistoryId());
        oncHistory.put(ONC_HISTORY_CREATED, oncHistoryDto.getCreated());

        Map<String, Object> parent = new HashMap<>();
        parent.put(ONC_HISTORY, oncHistory);
        Map<String, Object> update = Map.of(ESObjectConstants.DSM, parent);

        elasticUpdater.update(update, ddpParticipantId);
    }
}
