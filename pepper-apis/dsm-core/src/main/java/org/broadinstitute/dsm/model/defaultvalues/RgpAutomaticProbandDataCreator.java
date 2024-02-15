package org.broadinstitute.dsm.model.defaultvalues;

import org.broadinstitute.dsm.exception.ESMissingParticipantDataException;
import org.broadinstitute.dsm.model.bookmark.Bookmark;
import org.broadinstitute.dsm.service.participantdata.RgpParticipantDataService;


public class RgpAutomaticProbandDataCreator extends BasicDefaultDataMaker {

    /**
     * Create default data for an RGP participant. Expects an ES profile to exist for the participant.
     *
     * @throws ESMissingParticipantDataException if the participant does not have an ES profile. Can be used by
     *                                         callers to retry after waiting for the ES profile to be created.
     */
    @Override
    protected boolean setDefaultData(String ddpParticipantId) {
        // ensure we can get a family ID before writing things to the DB (to avoid concurrency issues)
        // This will increment the family ID value but will leave an unused family ID if we abort later.
        // As things stand now that is not a concern.
        long familyId = RgpParticipantDataService.getNextFamilyId(ddpParticipantId, new Bookmark());

        RgpParticipantDataService.createDefaultData(ddpParticipantId, elasticSearchParticipantDto, familyId, instance);
        return true;
    }
}
