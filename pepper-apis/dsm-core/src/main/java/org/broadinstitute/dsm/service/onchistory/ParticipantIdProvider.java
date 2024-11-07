package org.broadinstitute.dsm.service.onchistory;

import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;

import java.util.Optional;

public interface ParticipantIdProvider {

    /**
     * Given a participant short ID return a participant ID
     */
    int getParticipantIdForShortId(String shortId);

    Optional<ElasticSearchParticipantDto> getParticipantDataForShortId(String shortId);
}
