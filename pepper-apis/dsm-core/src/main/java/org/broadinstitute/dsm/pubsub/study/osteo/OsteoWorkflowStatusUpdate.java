package org.broadinstitute.dsm.pubsub.study.osteo;

import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.util.MedicalRecordUtil;
import org.broadinstitute.dsm.util.SystemUtil;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.broadinstitute.dsm.util.SystemUtil.SYSTEM;

public class OsteoWorkflowStatusUpdate {

    private final ParticipantDao participantDao;
    private final DDPInstance instance;
    private final String ddpParticipantId;
    private final int ddpInstanceId;

    private OsteoWorkflowStatusUpdate(ParticipantDao participantDao, DDPInstance instance, String ddpParticipantId) {
        this.participantDao = participantDao;
        this.instance = instance;
        this.ddpParticipantId = ddpParticipantId;
        this.ddpInstanceId = instance.getDdpInstanceIdAsInt();
    }

    public static OsteoWorkflowStatusUpdate of(ParticipantDao participantDao, DDPInstance instance, String ddpParticipantId) {
        return new OsteoWorkflowStatusUpdate(participantDao, instance, ddpParticipantId);
    }

    public void runWorkflow() {

        String ddpInstanceId = instance.getDdpInstanceId();
        boolean isParticipantInDb = MedicalRecordUtil.isParticipantInDB(ddpParticipantId, ddpInstanceId);

        if(!isParticipantInDb) {

            Optional<Integer> maybeNewOsteoParticipantId = participantDao.getParticipantByDdpParticipantIdAndDdpInstanceId(ddpParticipantId, Integer.parseInt(ddpInstanceId))
                    .map(this::copyParticipantDto)
                    .map(participantDao::create);







            //todo write into ddp_participant
            //todo write into ddp_participant_record
            //todo write into ES
            Map<String, List<MedicalRecord>> medicalRecords = MedicalRecord.getMedicalRecords(instance.getName(),
                    " AND p.ddp_participant_id = '" + ddpParticipantId + "'");
            if ( medicalRecords != null) {
                List<MedicalRecord> medicalRecordList = medicalRecords.get(ddpParticipantId);
                medicalRecordList.forEach(medicalRecord -> {
                    //todo writen into ddp_institution
                    //todo write into ddp_medical_record
                    //todo write into ES
                });
            }
        }
    }

    private ParticipantDto copyParticipantDto(ParticipantDto oldOsteoParticipant) {
        return new ParticipantDto.Builder()
                .withParticipantId(oldOsteoParticipant.getParticipantId().orElseThrow(DataCopyingException.withMessage("participant_id")))
                .withDdpParticipantId(oldOsteoParticipant.getDdpParticipantId().orElseThrow(DataCopyingException.withMessage("ddp_instance_id")))
                .withLastVersion(oldOsteoParticipant.getLastVersion().orElseThrow())
                .withLastVersionDate(oldOsteoParticipant.getLastVersionDate().orElse(null))
                .withDdpInstanceId(ddpInstanceId)
                .withReleaseCompleted(oldOsteoParticipant.getReleaseCompleted().orElse(false))
                .withAssigneeIdMr(oldOsteoParticipant.getAssigneeIdMr().orElseThrow(DataCopyingException.withMessage("assignee_id_mr")))
                .withAssigneeIdTissue(oldOsteoParticipant.getAssigneeIdTissue().orElseThrow(DataCopyingException.withMessage("assignee_id_tissue")))
                .withLastChanged(oldOsteoParticipant.getLastChanged())
                .withChangedBy(oldOsteoParticipant.getChangedBy().orElse(SYSTEM))
                .build();
    }
}
