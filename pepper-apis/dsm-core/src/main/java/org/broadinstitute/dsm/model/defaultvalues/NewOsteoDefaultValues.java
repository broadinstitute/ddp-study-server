package org.broadinstitute.dsm.model.defaultvalues;

import org.broadinstitute.dsm.exception.ESMissingParticipantDataException;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.service.participant.OsteoParticipantService;

/**
 * Sets default values for newly registered OS PE-CGS (OS2) participants.
 */
public class NewOsteoDefaultValues extends BasicDefaultDataMaker {

    @Override
    protected boolean setDefaultData(String ddpParticipantId, ElasticSearchParticipantDto esParticipant,
                                     String payload) {
        if (esParticipant.getDsm().isEmpty() || esParticipant.getActivities().isEmpty()) {
            throw new ESMissingParticipantDataException(String.format("Participant %s does not yet have DSM data and "
                    + "activities in ES", ddpParticipantId));
        }
        OsteoParticipantService osteoParticipantService = new OsteoParticipantService();
        osteoParticipantService.setOsteoDefaultData(ddpParticipantId, esParticipant, payload);
        return true;
    }
}
