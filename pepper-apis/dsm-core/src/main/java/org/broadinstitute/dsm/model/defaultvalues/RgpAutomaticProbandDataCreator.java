package org.broadinstitute.dsm.model.defaultvalues;

import org.broadinstitute.dsm.exception.ESMissingParticipantDataException;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.service.participantdata.RgpFamilyIdProvider;
import org.broadinstitute.dsm.service.participantdata.RgpParticipantDataService;

public class RgpAutomaticProbandDataCreator extends BasicDefaultDataMaker {

    /**
     * Create default data for an RGP participant. Expects an ES profile to exist for the participant.
     *
     * @throws ESMissingParticipantDataException if the participant does not have an ES profile. Can be used by
     *                                         callers to retry after waiting for the ES profile to be created.
     */
    @Override
    protected boolean setDefaultData(String ddpParticipantId, ElasticSearchParticipantDto esParticipant, String payload) {
        RgpParticipantDataService.createDefaultData(ddpParticipantId, esParticipant, instance,
                new RgpFamilyIdProvider());
        return true;
    }
}
