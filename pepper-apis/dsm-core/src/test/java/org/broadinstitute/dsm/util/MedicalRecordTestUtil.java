package org.broadinstitute.dsm.util;

import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dao.ddp.institution.DDPInstitutionDao;
import org.broadinstitute.dsm.db.dao.ddp.medical.records.MedicalRecordDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;

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

}
