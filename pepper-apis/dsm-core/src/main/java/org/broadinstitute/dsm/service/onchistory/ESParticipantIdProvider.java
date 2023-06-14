package org.broadinstitute.dsm.service.onchistory;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

@Slf4j
public class ESParticipantIdProvider implements ParticipantIdProvider {

    private final String realm;
    private final String participantIndex;

    public ESParticipantIdProvider(String realm, String participantIndex) {
        this.realm = realm;
        this.participantIndex = participantIndex;
    }

    public int getParticipantIdForShortId(String shortId) {
        Map<String, Map<String, Object>> ptpData;
        try {
            ptpData = ElasticSearchUtil.getSingleParticipantFromES(realm, participantIndex,
                    ElasticSearchUtil.getClientInstance(), shortId);
        } catch (Exception e) {
            log.info("ES threw exception for search of shortId {}: {}", shortId, e.toString());
            return -1;
        }

        if (ptpData.size() != 1) {
            log.info("ES returned {} results for shortId {}", ptpData.size(), shortId);
            return -1;
        }
        Map<String, Object> ptp = ptpData.values().stream().findFirst().orElseThrow();
        Map<String, Object> dsm = (Map<String, Object>) ptp.get("dsm");
        if (dsm == null || dsm.isEmpty()) {
            log.info("ES returned empty dsm object for shortId {}", shortId);
            return -1;
        }
        Map<String, Object> dsmParticipant = (Map<String, Object>) dsm.get("participant");
        if (dsmParticipant == null || dsmParticipant.isEmpty()) {
            log.info("ES returned empty dsm.participant object for shortId {}", shortId);
            return -1;
        }
        Object participantID = dsmParticipant.get("participantId");
        if (participantID == null) {
            log.info("ES did not have a participantID for shortId {}", shortId);
            return -1;
        }

        try {
            return Integer.parseInt(participantID.toString());
        } catch (Exception e) {
            log.info("ES did not have a valid participantID for shortId {}", shortId);
            return -1;
        }
    }
}
