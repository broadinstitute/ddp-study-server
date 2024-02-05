package org.broadinstitute.dsm.util;

import static org.broadinstitute.dsm.statics.ESObjectConstants.ONC_HISTORY;
import static org.broadinstitute.dsm.statics.ESObjectConstants.ONC_HISTORY_CREATED;
import static org.broadinstitute.dsm.statics.ESObjectConstants.ONC_HISTORY_ID;
import static org.broadinstitute.dsm.statics.ESObjectConstants.PARTICIPANT_ID;

import java.util.HashMap;
import java.util.Map;

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

public class MedicalRecordTestUtil {

    public static int createMedicalRecord(ParticipantDto participant, DDPInstanceDto instanceDto) {
        return OncHistoryDetail.verifyOrCreateMedicalRecord(participant.getParticipantIdOrThrow(),
                participant.getDdpParticipantIdOrThrow(), instanceDto.getInstanceName(), true);
    }

    public static void deleteMedicalRecord(int medicalRecordId) {
        MedicalRecordDao medicalRecordDao = new MedicalRecordDao();
        MedicalRecord medicalRecord = medicalRecordDao.get(medicalRecordId).get();
        medicalRecordDao.delete(medicalRecordId);
        DDPInstitutionDao ddpInstitutionDao = new DDPInstitutionDao();
        ddpInstitutionDao.delete(medicalRecord.getInstitutionId());
    }

    public static int createOncHistoryDetail(String ddpParticipantId, OncHistoryDetail rec, String esIndex) {
        int oncHistoryDetailId = OncHistoryDetail.createOncHistoryDetail(rec);
        rec.setOncHistoryDetailId(oncHistoryDetailId);
        ElasticTestUtil.createOncHistoryDetail(esIndex, rec, ddpParticipantId);
        return oncHistoryDetailId;
    }

    public static void deleteOncHistoryDetail(int oncHistoryDetailId) {
        OncHistoryDetailDaoImpl dao = new OncHistoryDetailDaoImpl();
        dao.delete(oncHistoryDetailId);
    }

    public static int createOncHistory(String ddpParticipantId, OncHistoryDto oncHistoryDto, String esIndex) {
        OncHistoryDao oncHistoryDao = new OncHistoryDao();
        int oncHistoryId = oncHistoryDao.create(oncHistoryDto);
        oncHistoryDto.setOncHistoryId(oncHistoryId);

        Map<String, Object> oncHistory = new HashMap<>();
        oncHistory.put(PARTICIPANT_ID, oncHistoryDto.getParticipantId());
        oncHistory.put(ONC_HISTORY_ID, oncHistoryDto.getOncHistoryId());
        oncHistory.put(ONC_HISTORY_CREATED, oncHistoryDto.getCreated());

        Map<String, Object> parent = new HashMap<>();
        parent.put(ONC_HISTORY, oncHistory);
        Map<String, Object> update = Map.of(ESObjectConstants.DSM, parent);

        OncHistoryElasticUpdater elasticUpdater = new OncHistoryElasticUpdater(esIndex);
        elasticUpdater.update(update, ddpParticipantId);
        return oncHistoryId;
    }

    public static void deleteOncHistory(int oncHistoryId) {
        OncHistoryDao oncHistoryDao = new OncHistoryDao();
        oncHistoryDao.delete(oncHistoryId);
    }
}
