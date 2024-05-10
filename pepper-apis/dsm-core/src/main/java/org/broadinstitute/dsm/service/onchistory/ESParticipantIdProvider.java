package org.broadinstitute.dsm.service.onchistory;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;

@Slf4j
public class ESParticipantIdProvider implements ParticipantIdProvider {

    private final String realm;
    private final String participantIndex;

    public ESParticipantIdProvider(String realm, String participantIndex) {
        this.realm = realm;
        this.participantIndex = participantIndex;
    }

    /**
     * Given a participant short ID return a participant ID
     * @throws DsmInternalError for bad ES behavior
     * @throws DSMBadRequestException when no participant ID is found for short ID
     */
    public int getParticipantIdForShortId(String shortId) {
        Map<String, Object> dsm = ElasticSearchService.getDsmForSingleParticipant(realm, participantIndex,
                shortId);
        Map<String, Object> dsmParticipant = (Map<String, Object>) dsm.get("participant");
        if (dsmParticipant == null || dsmParticipant.isEmpty()) {
            throw new DsmInternalError("ES returned empty dsm.participant object for shortId " + shortId);
        }
        Object participantID = dsmParticipant.get("participantId");
        if (participantID == null) {
            throw new DsmInternalError("ES returned empty dsm.participant.participantId object for shortId " + shortId);
        }

        try {
            return Integer.parseInt(participantID.toString());
        } catch (Exception e) {
            throw new DsmInternalError("Invalid dsm.participant.participantId for shortId " + shortId);
        }
    }
}
