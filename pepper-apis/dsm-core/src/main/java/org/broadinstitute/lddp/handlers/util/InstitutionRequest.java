package org.broadinstitute.lddp.handlers.util;

import java.util.List;

public class InstitutionRequest {
    private long id; //submissionId of survey
    private String participantId; //UUID (alt pid)
    private String lastUpdated; //last updated date for institution survey
    private List<Institution> institutions; //institutions

    public InstitutionRequest() {
    }

    public InstitutionRequest(long id, String participantId, List<Institution> institution, String lastUpdated) {
        this.id = id;
        this.participantId = participantId;
        this.institutions = institution;
        this.lastUpdated = lastUpdated;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getParticipantId() {
        return participantId;
    }

    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }

    public String getLastUpdated() {
        return this.lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public List<Institution> getInstitutions() {
        return institutions;
    }

    public void setInstitutions(List<Institution> institutions) {
        this.institutions = institutions;
    }

}

