package org.broadinstitute.dsm.service.medicalrecord;

import java.sql.Connection;
import java.util.Optional;

import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.service.participant.OsteoParticipantService;


public class TestMedicalRecordInstanceProvider extends MedicalRecordInstanceProvider {
    private final OsteoParticipantService osteoParticipantService;
    private long sequenceNumber;

    public TestMedicalRecordInstanceProvider(OsteoParticipantService osteoParticipantService, long sequenceStart) {
        super();
        this.osteoParticipantService = osteoParticipantService;
        this.sequenceNumber = sequenceStart;
    }

    @Override
    public DDPInstanceDto getEffectiveInstance(DDPInstance ddpInstance, String ddpParticipantId) {
        // no special handling for throw since this is an invariant
        DDPInstanceDto ddpInstanceDto = ddpInstanceDao.getDDPInstanceByInstanceName(ddpInstance.getName())
                .orElseThrow();

        if (osteoParticipantService.isOsteoInstance(ddpInstanceDto)
                && osteoParticipantService.isOnlyOsteo1Participant(ddpParticipantId)) {
            return osteoParticipantService.getOsteo1Instance();
        }
        return ddpInstanceDto;
    }

    @Override
    public Optional<Long> getInstanceSequenceNumber(DDPInstance ddpInstance) {
        return Optional.of(sequenceNumber);
    }

    @Override
    public Long getInstanceSequenceNumber(DDPInstance ddpInstance, Connection conn) {
        return sequenceNumber;
    }

    @Override
    public void updateInstanceSequenceNumber(DDPInstance ddpInstance, long sequenceNumber, Connection conn) {
        if (sequenceNumber <= this.sequenceNumber) {
            throw new IllegalArgumentException(
                    "Sequence number provided %d was not greater than the current sequence number %d"
                            .formatted(sequenceNumber, this.sequenceNumber));
        }
        this.sequenceNumber = sequenceNumber;
    }
}
