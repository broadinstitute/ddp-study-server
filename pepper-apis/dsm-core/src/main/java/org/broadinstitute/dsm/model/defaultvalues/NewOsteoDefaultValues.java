package org.broadinstitute.dsm.model.defaultvalues;

import org.broadinstitute.dsm.exception.ESMissingParticipantDataException;
import org.broadinstitute.dsm.service.participant.OsteoParticipantService;

/**
 * Sets default values for newly registered OS PE-CGS (OS2) participants.
 */
public class NewOsteoDefaultValues extends BasicDefaultDataMaker {

    @Override
    protected boolean setDefaultData(String ddpParticipantId) {
        if (elasticSearchParticipantDto.getDsm().isEmpty()) {
            throw new ESMissingParticipantDataException("Participant dsm ES data missing");
        }
        OsteoParticipantService osteoParticipantService = new OsteoParticipantService();
        osteoParticipantService.setOsteo2DefaultData(ddpParticipantId, elasticSearchParticipantDto);
        return true;
    }
}
