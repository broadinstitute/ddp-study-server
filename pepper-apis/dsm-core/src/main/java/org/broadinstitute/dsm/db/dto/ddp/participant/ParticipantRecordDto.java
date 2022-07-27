package org.broadinstitute.dsm.db.dto.ddp.participant;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

@TableName(name = DBConstants.DDP_PARTICIPANT_RECORD, alias = DBConstants.DDP_PARTICIPANT_RECORD_ALIAS,
        primaryKey = DBConstants.PARTICIPANT_RECORD_ID, columnPrefix = "")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParticipantRecordDto implements Cloneable {

    @ColumnName(DBConstants.PARTICIPANT_RECORD_ID)
    private Integer participantRecordId;

    @ColumnName(DBConstants.PARTICIPANT_ID)
    private int participantId;

    @ColumnName(DBConstants.CR_SENT)
    private String crSent;

    @ColumnName(DBConstants.CR_RECEIVED)
    private String crReceived;

    @ColumnName(DBConstants.NOTES)
    private String notes;

    @ColumnName(DBConstants.MINIMAL_MR)
    private Integer minimalMr;

    @ColumnName(DBConstants.ABSTRACTION_READY)
    private Integer abstractionReady;

    @ColumnName(DBConstants.ADDITIONAL_VALUES_JSON)
    @JsonProperty("dynamicFields")
    private String additionalValuesJson;
    private long lastChanged;
    private String changedBy;

    public ParticipantRecordDto() {

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

    public void setParticipantId(int participantId) {
        this.participantId = participantId;
    }

    @JsonProperty("dynamicFields")
    public Map<String, Object> getDynamicFields() {
        try {
            return ObjectMapperSingleton.instance().readValue(additionalValuesJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException | NullPointerException e) {
            return Map.of();
        }
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

    @Override
    public ParticipantRecordDto clone() {
        try {
            return (ParticipantRecordDto) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
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

        public Builder() {

        }

        public Builder(int participantId, long lastChanged) {
            this.participantId = participantId;
            this.lastChanged = lastChanged;
        }

        public Builder withParticipantId(int participantId) {
            this.participantId = participantId;
            return this;
        }

        public Builder withLastChanged(long lastChanged) {
            this.lastChanged = lastChanged;
            return this;
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

        public ParticipantRecordDto build() {
            return new ParticipantRecordDto(this);
        }

    }
}
