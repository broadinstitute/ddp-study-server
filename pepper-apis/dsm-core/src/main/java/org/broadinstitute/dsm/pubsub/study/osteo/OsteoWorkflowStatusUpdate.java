package org.broadinstitute.dsm.pubsub.study.osteo;

import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.dao.ddp.institution.DDPInstitutionDao;
import org.broadinstitute.dsm.db.dao.ddp.medical.records.MedicalRecordDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantRecordDao;
import org.broadinstitute.dsm.db.dto.ddp.institution.DDPInstitutionDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantRecordDto;
import org.broadinstitute.dsm.pubsub.study.HasWorkflowStatusUpdate;
import org.broadinstitute.dsm.util.MedicalRecordUtil;

import java.util.List;
import java.util.Optional;

import static org.broadinstitute.dsm.statics.DBConstants.*;
import static org.broadinstitute.dsm.util.SystemUtil.SYSTEM;

public class OsteoWorkflowStatusUpdate implements HasWorkflowStatusUpdate {

    private final DDPInstance instance;
    private final String ddpParticipantId;
    private final int ddpInstanceId;

    private ParticipantDao participantDao;
    private ParticipantRecordDao participantRecordDao;
    private DDPInstitutionDao ddpInstitutionDao;
    private MedicalRecordDao medicalRecordDao;

    private OsteoWorkflowStatusUpdate(DDPInstance instance, String ddpParticipantId) {
        this.instance = instance;
        this.ddpParticipantId = ddpParticipantId;
        this.ddpInstanceId = instance.getDdpInstanceIdAsInt();
        this.participantDao = ParticipantDao.of();
        this.participantRecordDao = ParticipantRecordDao.of();
        this.ddpInstitutionDao = DDPInstitutionDao.of();
        this.medicalRecordDao = MedicalRecordDao.of();
    }

    public static OsteoWorkflowStatusUpdate of(DDPInstance instance, String ddpParticipantId) {
        return new OsteoWorkflowStatusUpdate(instance, ddpParticipantId);
    }

    @Override
    public void update() {

        String ddpInstanceId = instance.getDdpInstanceId();
        boolean isParticipantInDb = MedicalRecordUtil.isParticipantInDB(ddpParticipantId, ddpInstanceId);

        if (!isParticipantInDb) {

            Optional<ParticipantDto> maybeOldOsteoParticipant = participantDao.getParticipantByDdpParticipantIdAndDdpInstanceId(ddpParticipantId, Integer.parseInt(ddpInstanceId));
            Optional<Integer> maybeOldOsteoParticipantId = maybeOldOsteoParticipant.flatMap(ParticipantDto::getParticipantId);

            Optional<Integer> maybeNewOsteoParticipantId = maybeOldOsteoParticipant
                    .map(this::updateParticipantDto)
                    .map(participantDao::create);

            Optional<ParticipantRecordDto> maybeOldOsteoParticipantRecord = maybeOldOsteoParticipantId.flatMap(participantRecordDao::getParticipantRecordByParticipantId);

            maybeOldOsteoParticipantRecord.ifPresent(participantRecord ->
                    maybeNewOsteoParticipantId.ifPresent(participantId ->
                            updateAndThenSaveNewParticipantRecord(participantRecord, participantId)));

            maybeNewOsteoParticipantId.ifPresent(this::updateAndThenSaveInstitutionsAndMedicalRecords);



        }
    }

    private void updateAndThenSaveInstitutionsAndMedicalRecords(int newOsteoParticipantId) {
        List<MedicalRecord> oldOsteoMedicalRecords = MedicalRecord.getMedicalRecordsByInstanceNameAndDdpParticipantId(instance.getName(), ddpParticipantId);
        oldOsteoMedicalRecords.forEach(medicalRecord -> {
            int newOsteoInstitutionId = updateAndThenSaveNewInstitution(newOsteoParticipantId, medicalRecord.getInstitutionId());
            medicalRecord.setInstitutionId(newOsteoInstitutionId);
            medicalRecordDao.create(medicalRecord);
        });
    }

    private int updateAndThenSaveNewInstitution(int newOsteoParticipantId, long institutionId) {
        return ddpInstitutionDao.get(institutionId)
                .map(oldOsteoInstitution -> updateInstitution(newOsteoParticipantId, oldOsteoInstitution))
                .map(ddpInstitutionDao::create)
                .orElseThrow();
    }

    private DDPInstitutionDto updateInstitution(int newOsteoParticipantId, DDPInstitutionDto institution) {
        return new DDPInstitutionDto.Builder()
                .withType(institution.getType())
                .withLastChanged(institution.getLastChanged())
                .withDdpInstitutionId(institution.getDdpInstitutionId())
                .withInstitutionId(institution.getInstitutionId())
                .withParticipantId(newOsteoParticipantId).build();
    }

    private void updateAndThenSaveNewParticipantRecord(ParticipantRecordDto oldOsteoParticipantRecord, int newOsteoParticipantId) {
        ParticipantRecordDto participantRecordDto = updateParticipantRecord(newOsteoParticipantId, oldOsteoParticipantRecord);
        participantRecordDao.create(participantRecordDto);
    }

    private ParticipantRecordDto updateParticipantRecord(int newOsteoParticipantId, ParticipantRecordDto oldOsteoParticipantRecord) {
        return new ParticipantRecordDto.Builder()
                .withParticipantId(newOsteoParticipantId)
                .withLastChanged(oldOsteoParticipantRecord.getLastChanged())
                .withParticipantRecordId(oldOsteoParticipantRecord.getParticipantRecordId().orElseThrow(DataCopyingException.withMessage(PARTICIPANT_RECORD_ID)))
                .withCrSent(oldOsteoParticipantRecord.getCrSent().orElse(null))
                .withCrReceived(oldOsteoParticipantRecord.getCrReceived().orElse(null))
                .withNotes(oldOsteoParticipantRecord.getNotes().orElse(null))
                .withMinimalMr(oldOsteoParticipantRecord.getMinimalMr().orElseThrow(DataCopyingException.withMessage(MINIMAL_MR)))
                .withAbstractionReady(oldOsteoParticipantRecord.getAbstractionReady().orElseThrow(DataCopyingException.withMessage(ABSTRACTION_READY)))
                .withAdditionalValuesJson(oldOsteoParticipantRecord.getAdditionalValuesJson().orElse(null))
                .withChangedBy(oldOsteoParticipantRecord.getChangedBy().orElse(SYSTEM))
                .build();
    }

    private ParticipantDto updateParticipantDto(ParticipantDto participantDto) {
        return new ParticipantDto.Builder()
                .withParticipantId(participantDto.getParticipantId().orElseThrow(DataCopyingException.withMessage(PARTICIPANT_ID)))
                .withDdpParticipantId(participantDto.getDdpParticipantId().orElseThrow(DataCopyingException.withMessage(DDP_INSTANCE_ID)))
                .withLastVersion(participantDto.getLastVersion().orElseThrow())
                .withLastVersionDate(participantDto.getLastVersionDate().orElse(null))
                .withDdpInstanceId(ddpInstanceId)
                .withReleaseCompleted(participantDto.getReleaseCompleted().orElse(false))
                .withAssigneeIdMr(participantDto.getAssigneeIdMr().orElseThrow(DataCopyingException.withMessage(ASSIGNEE_ID_MR)))
                .withAssigneeIdTissue(participantDto.getAssigneeIdTissue().orElseThrow(DataCopyingException.withMessage(ASSIGNEE_ID_TISSUE)))
                .withLastChanged(participantDto.getLastChanged())
                .withChangedBy(participantDto.getChangedBy().orElse(SYSTEM))
                .build();
    }

}
