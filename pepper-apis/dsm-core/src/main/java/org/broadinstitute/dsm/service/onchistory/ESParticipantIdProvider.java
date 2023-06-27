package org.broadinstitute.dsm.service.onchistory;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

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
        Map<String, Map<String, Object>> ptpData;
        try {
            ptpData = ElasticSearchUtil.getSingleParticipantFromES(realm, participantIndex,
                    ElasticSearchUtil.getClientInstance(), shortId);
        } catch (Exception e) {
            throw new DsmInternalError("ES threw exception for search of shortId: " + shortId, e);
        }

        if (ptpData.size() > 1) {
            String msg = String.format("ES returned %d results for participant shortId %s", ptpData.size(), shortId);
            throw new DsmInternalError(msg);
        }
        if (ptpData.size() == 0) {
            throw new DSMBadRequestException("Invalid participant ID " + shortId);
        }
        Map<String, Object> ptp = ptpData.values().stream().findFirst().orElseThrow();
        Map<String, Object> dsm = (Map<String, Object>) ptp.get("dsm");
        if (dsm == null || dsm.isEmpty()) {
            throw new DsmInternalError("ES returned empty dsm object for shortId " + shortId);
        }
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
