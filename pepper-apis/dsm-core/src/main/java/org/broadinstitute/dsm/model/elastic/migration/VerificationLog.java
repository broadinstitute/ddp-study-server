package org.broadinstitute.dsm.model.elastic.migration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Data verification log entry for Elastic export
 */
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class VerificationLog {

    public enum VerificationStatus {
        VERIFIED,
        ES_DATA_MISMATCH,
        ES_ENTITY_MISMATCH,
        NO_ES_DOCUMENT,
        ERROR
    }

    private final String ddpParticipantId;
    private final String entity;
    private String entityId;
    private VerificationStatus status;
    private String message;
    private Set<String> dbOnlyEntityIds = new HashSet<>();
    private Set<String> esOnlyEntityIds = new HashSet<>();
    private Set<String> commonFields = new HashSet<>();
    private Set<String> dbOnlyFields = new HashSet<>();
    private Set<String> esOnlyFields = new HashSet<>();
    private Map<String, Pair<String, String>> differentValues = new HashMap<>();


    public VerificationLog(String ddpParticipantId, String entity, VerificationStatus status) {
        this.ddpParticipantId = ddpParticipantId;
        this.entity = entity;
        this.status = status;
    }

    public VerificationLog(String ddpParticipantId, String entity) {
        this.ddpParticipantId = ddpParticipantId;
        this.entity = entity;
        this.status = VerificationStatus.VERIFIED;
    }

    public VerificationLog(String ddpParticipantId, String entity, String entityId) {
        this.ddpParticipantId = ddpParticipantId;
        this.entity = entity;
        this.entityId = entityId;
        this.status = VerificationStatus.VERIFIED;
    }

    public void setError(String message) {
        this.message = message;
        this.status = VerificationStatus.ERROR;
    }

    public void setDbOnlyEntityIds(Set<String> dbOnlyEntityIds) {
        this.dbOnlyEntityIds = dbOnlyEntityIds;
        this.status = VerificationStatus.ES_ENTITY_MISMATCH;
    }

    public void setEsOnlyEntityIds(Set<String> esOnlyEntityIds) {
        this.esOnlyEntityIds = esOnlyEntityIds;
        this.status = VerificationStatus.ES_ENTITY_MISMATCH;
    }

    public void setEsOnlyFields(Set<String> esOnlyFields) {
        this.esOnlyFields = esOnlyFields;
        this.status = VerificationStatus.ES_DATA_MISMATCH;
    }

    public void setDbOnlyFields(Set<String> dbOnlyFields) {
        this.dbOnlyFields = dbOnlyFields;
        this.status = VerificationStatus.ES_DATA_MISMATCH;
    }

    public void setDifferentValues(String field, String dbValue, String esValue) {
        Pair<String, String> pair = new ImmutablePair<>(dbValue, esValue);
        differentValues.put(field, pair);
        this.status = VerificationStatus.ES_DATA_MISMATCH;
    }
}
