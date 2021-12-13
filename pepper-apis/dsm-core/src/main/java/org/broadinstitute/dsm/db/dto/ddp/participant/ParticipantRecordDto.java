package org.broadinstitute.dsm.db.dto.ddp.participant;

import java.util.Optional;

import lombok.Getter;

@Getter
public class ParticipantRecordDto {

    private Optional<Integer> participantRecordId;
    private int participantId;
    private Optional<String> crSent;
    private Optional<String> crReceived;
    private Optional<String> notes;
    private Optional<Integer> minimalMr;
    private Optional<Integer> abstractionReady;
    private Optional<String> additionalValuesJson;
    private long lastChanged;
    private Optional<String> changedBy;

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
        public Optional<Integer> participantRecordId = Optional.empty();
        public int participantId;
        public Optional<String> crSent = Optional.empty();
        public Optional<String> crReceived = Optional.empty();
        public Optional<String> notes = Optional.empty();
        public Optional<Integer> minimalMr = Optional.empty();
        public Optional<Integer> abstractionReady = Optional.empty();
        public Optional<String> additionalValuesJson = Optional.empty();
        public long lastChanged;
        public Optional<String> changedBy = Optional.empty();

        public Builder(int participantId, long lastChanged) {
            this.participantId = participantId;
            this.lastChanged = lastChanged;
        }

        public Builder withParticipantRecordId(int participantRecordId) {
            this.participantRecordId = Optional.of(participantRecordId);
            return this;
        }

        public Builder withCrSent(String crSent) {
            this.crSent = Optional.ofNullable(crSent);
            return this;
        }

        public Builder withCrReceived(String crReceived) {
            this.crReceived = Optional.ofNullable(crReceived);
            return this;
        }

        public Builder withNotes(String notes) {
            this.notes = Optional.ofNullable(notes);
            return this;
        }

        public Builder withMinimalMr(int minimalMr) {
            this.minimalMr = Optional.of(minimalMr);
            return this;
        }

        public Builder withAbstractionReady(int abstractionReady) {
            this.abstractionReady = Optional.of(abstractionReady);
            return this;
        }

        public Builder withAdditionalValuesJson(String additionalValuesJson) {
            this.additionalValuesJson = Optional.ofNullable(additionalValuesJson);
            return this;
        }

        public Builder withChangedBy(String changedBy) {
            this.changedBy = Optional.ofNullable(changedBy);
            return this;
        }

        public ParticipantRecordDto builder() {
            return new ParticipantRecordDto(this);
        }

    }
}
