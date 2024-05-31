package org.broadinstitute.dsm.route.request;

import lombok.Data;

@Data
public class ParticipantExitRequest {
    private final String realm;
    private final String participantId;
    private final String user;
    private final boolean inDDP;
    private final String exitDate;
    private final String shortId;
    private final String legacyShortId;

    public ParticipantExitRequest(String realm, String participantId, String user, boolean inDDP) {
        this.realm = realm;
        this.participantId = participantId;
        this.user = user;
        this.inDDP = inDDP;
        this.exitDate = null;
        this.shortId = null;
        this.legacyShortId = null;
    }
}
