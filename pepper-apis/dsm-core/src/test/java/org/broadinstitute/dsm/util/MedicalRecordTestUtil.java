package org.broadinstitute.dsm.util;

import static org.broadinstitute.dsm.statics.ESObjectConstants.ONC_HISTORY;
import static org.broadinstitute.dsm.statics.ESObjectConstants.ONC_HISTORY_CREATED;
import static org.broadinstitute.dsm.statics.ESObjectConstants.ONC_HISTORY_ID;
import static org.broadinstitute.dsm.statics.ESObjectConstants.PARTICIPANT_ID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dao.ddp.institution.DDPInstitutionDao;
import org.broadinstitute.dsm.db.dao.ddp.medical.records.MedicalRecordDao;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDao;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDaoImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.onchistory.OncHistoryDto;
import org.broadinstitute.dsm.service.onchistory.OncHistoryElasticUpdater;
import org.broadinstitute.dsm.statics.ESObjectConstants;

@Slf4j
public class MedicalRecordTestUtil {
    private final Map<Integer, List<Integer>> participantToMedicalRecordId = new HashMap<>();
    private final Map<Integer, List<Integer>> participantToOncHistoryDetailId = new HashMap<>();
    private final Map<Integer, Integer> participantToOncHistoryId = new HashMap<>();

    public MedicalRecordTestUtil() { }

    public void tearDown() {
        participantToOncHistoryId.values().forEach(MedicalRecordTestUtil::deleteOncHistory);
        participantToOncHistoryId.clear();

        participantToOncHistoryDetailId.values().forEach(ids ->
                ids.forEach(MedicalRecordTestUtil::deleteOncHistoryDetail));
        participantToOncHistoryDetailId.clear();

        participantToMedicalRecordId.values().forEach(ids ->
                ids.forEach(MedicalRecordTestUtil::deleteMedicalRecord));
        participantToMedicalRecordId.clear();
    }

    public int createMedicalRecord(ParticipantDto participant, DDPInstanceDto instanceDto) {
        int medicalId = OncHistoryDetail.verifyOrCreateMedicalRecord(participant.getParticipantIdOrThrow(),
                participant.getDdpParticipantIdOrThrow(), instanceDto.getInstanceName(), true);
        addMedicalRecord(participant.getRequiredParticipantId(), medicalId);
        return medicalId;
    }

    public static void deleteMedicalRecord(int medicalRecordId) {
        MedicalRecordDao medicalRecordDao = new MedicalRecordDao();
        MedicalRecord medicalRecord = medicalRecordDao.get(medicalRecordId).get();
        medicalRecordDao.delete(medicalRecordId);
        DDPInstitutionDao ddpInstitutionDao = new DDPInstitutionDao();
        ddpInstitutionDao.delete(medicalRecord.getInstitutionId());
    }

    public int createOncHistoryDetail(ParticipantDto participant, OncHistoryDetail rec, String esIndex) {
        int oncHistoryDetailId = OncHistoryDetail.createOncHistoryDetail(rec);
        rec.setOncHistoryDetailId(oncHistoryDetailId);
        ElasticTestUtil.createOncHistoryDetail(esIndex, rec, participant.getRequiredDdpParticipantId());
        addOncHistoryDetail(participant.getRequiredParticipantId(), oncHistoryDetailId);
        return oncHistoryDetailId;
    }

    public static void deleteOncHistoryDetail(int oncHistoryDetailId) {
        OncHistoryDetailDaoImpl dao = new OncHistoryDetailDaoImpl();
        dao.delete(oncHistoryDetailId);
    }

    public int createOncHistory(ParticipantDto participantDto, String userId, String esIndex) {
        int participantId = participantDto.getRequiredParticipantId();
        OncHistoryDto oncHistoryDto = new OncHistoryDto.Builder()
                .withParticipantId(participantId)
                .withChangedBy(userId)
                .withLastChangedNow()
                .withCreatedNow().build();
        OncHistoryDao oncHistoryDao = new OncHistoryDao();
        int oncHistoryId = oncHistoryDao.create(oncHistoryDto);
        oncHistoryDto.setOncHistoryId(oncHistoryId);

        Map<String, Object> oncHistory = new HashMap<>();
        oncHistory.put(PARTICIPANT_ID, participantId);
        oncHistory.put(ONC_HISTORY_ID, oncHistoryDto.getOncHistoryId());
        oncHistory.put(ONC_HISTORY_CREATED, oncHistoryDto.getCreated());

        Map<String, Object> parent = new HashMap<>();
        parent.put(ONC_HISTORY, oncHistory);
        Map<String, Object> update = Map.of(ESObjectConstants.DSM, parent);

        OncHistoryElasticUpdater elasticUpdater = new OncHistoryElasticUpdater(esIndex);
        elasticUpdater.update(update, participantDto.getRequiredDdpParticipantId());
        participantToOncHistoryId.put(participantId, oncHistoryId);
        return oncHistoryId;
    }

    public static void deleteOncHistory(int oncHistoryId) {
        OncHistoryDao oncHistoryDao = new OncHistoryDao();
        oncHistoryDao.delete(oncHistoryId);
    }

    public List<Integer> getParticipantMedicalIds(int participantId) {
        List<Integer> ids = participantToMedicalRecordId.get(participantId);
        if (ids == null) {
            return Collections.emptyList();
        }
        return ids;
    }

    public List<Integer> getParticipantOncHistoryDetailIds(int participantId) {
        List<Integer> ids = participantToOncHistoryDetailId.get(participantId);
        if (ids == null) {
            return Collections.emptyList();
        }
        return ids;
    }

    public Integer getParticipantOncHistoryId(int participantId) {
        return participantToOncHistoryId.get(participantId);
    }

    private void addMedicalRecord(int participantId, int medicalRecordId) {
        if (participantToMedicalRecordId.containsKey(participantId)) {
            participantToMedicalRecordId.get(participantId).add(medicalRecordId);
            return;
        }
        participantToMedicalRecordId.put(participantId, new ArrayList<>(List.of(medicalRecordId)));
    }

    private void addOncHistoryDetail(int participantId, int oncHistoryDetailId) {
        if (participantToOncHistoryDetailId.containsKey(participantId)) {
            participantToOncHistoryDetailId.get(participantId).add(oncHistoryDetailId);
            return;
        }
        participantToOncHistoryDetailId.put(participantId, new ArrayList<>(List.of(oncHistoryDetailId)));
    }
}
