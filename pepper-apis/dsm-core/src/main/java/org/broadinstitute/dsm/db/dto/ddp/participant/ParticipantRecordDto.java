package org.broadinstitute.dsm.db.dto.ddp.participant;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParticipantRecordDto {

    @ColumnName(DBConstants.PARTICIPANT_RECORD_ID)
    private Integer participantRecordId;

    @ColumnName (DBConstants.PARTICIPANT_ID)
    private int participantId;

    @ColumnName (DBConstants.CR_SENT)
    private String crSent;

    @ColumnName (DBConstants.CR_RECEIVED)
    private String crReceived;

    @ColumnName (DBConstants.NOTES)
    private String notes;

    @ColumnName (DBConstants.MINIMAL_MR)
    private Integer minimalMr;

    @ColumnName (DBConstants.ABSTRACTION_READY)
    private Integer abstractionReady;

    @ColumnName (DBConstants.ADDITIONAL_VALUES_JSON)
    @JsonProperty("dynamicFields")
    private String additionalValuesJson;

    @JsonProperty("dynamicFields")
    public Map<String, Object> getDynamicFields() {
        try {
            return ObjectMapperSingleton.instance().readValue(additionalValuesJson, new TypeReference<Map<String, Object>>() {});
        } catch (IOException | NullPointerException e) {
            return Map.of();
        }
    }


    private long lastChanged;
    private String changedBy;

    public ParticipantRecordDto() {

    }

    public Optional<Integer> getParticipantRecordId() {
        return Optional.ofNullable(participantRecordId);
    }

    public int getParticipantId() {
        return participantId;
    }

    public Optional<String> getCrSent() {
        return Optional.ofNullable(crSent);
    }

    public Optional<String> getCrReceived() {
        return Optional.ofNullable(crReceived);
    }

    public Optional<String> getNotes() {
        return Optional.ofNullable(notes);
    }

    public Optional<Integer> getMinimalMr() {
        return Optional.ofNullable(minimalMr);
    }

    public Optional<Integer> getAbstractionReady() {
        return Optional.ofNullable(abstractionReady);
    }

    public Optional<String> getAdditionalValuesJson() {
        return Optional.ofNullable(additionalValuesJson);
    }

    public long getLastChanged() {
        return lastChanged;
    }

    public Optional<String> getChangedBy() {
        return Optional.ofNullable(changedBy);
    }

    private ParticipantRecordDto(Builder builder) {
        this.participantRecordId = builder.participantRecordId;
        this.participantId = builder.participantId;
        this.crSent = builder.crSent;
        this.crReceived = builder.crReceived;
        this.notes = builder.notes;
        this.minimalMr = builder.minimalMr;
        this.abstractionReady = builder.abstractionReady;
        this.additionalValuesJson = builder.additionalValuesJson;
        this.lastChanged = builder.lastChanged;
        this.changedBy = builder.changedBy;
    }

    public static class Builder {
        public Integer participantRecordId;
        public int participantId;
        public String crSent;
        public String crReceived;
        public String notes;
        public Integer minimalMr;
        public Integer abstractionReady;
        public String additionalValuesJson;
        public long lastChanged;
        public String changedBy;
        
        public Builder(int participantId, long lastChanged) {
            this.participantId = participantId;
            this.lastChanged = lastChanged;
        }
        
        public Builder withParticipantRecordId(int participantRecordId) {
            this.participantRecordId = participantRecordId;
            return this;
        }

        public Builder withCrSent(String crSent) {
            this.crSent = crSent;
            return this;
        }

        public Builder withCrReceived(String crReceived) {
            this.crReceived = crReceived;
            return this;
        }

        public Builder withNotes(String notes) {
            this.notes = notes;
            return this;
        }

        public Builder withMinimalMr(int minimalMr) {
            this.minimalMr = minimalMr;
            return this;
        }

        public Builder withAbstractionReady(int abstractionReady) {
            this.abstractionReady = abstractionReady;
            return this;
        }

        public Builder withAdditionalValuesJson(String additionalValuesJson) {
            this.additionalValuesJson = additionalValuesJson;
            return this;
        }

        public Builder withChangedBy(String changedBy) {
            this.changedBy = changedBy;
            return this;
        }

        public ParticipantRecordDto builder() {
            return new ParticipantRecordDto(this);
        }

    }
}
