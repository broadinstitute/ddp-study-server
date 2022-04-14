package org.broadinstitute.dsm.db.dto.ddp.participant;

import java.util.Optional;

import lombok.Getter;

@Getter
public class ParticipantDto {


    private Optional<Integer> participantId;
    private Optional<String> ddpParticipantId;
    private Optional<Long> lastVersion;
    private Optional<String> lastVersionDate;
    private int ddpInstanceId;
    private Optional<Boolean> releaseCompleted;
    private Optional<Integer> assigneeIdMr;
    private Optional<Integer> assigneeIdTissue;
    private long lastChanged;
    private Optional<String> changedBy;

    private ParticipantDto(Builder builder) {
        this.participantId = builder.participantId;
        this.ddpParticipantId = builder.ddpParticipantId;
        this.lastVersion = builder.lastVersion;
        this.lastVersionDate = builder.lastVersionDate;
        this.ddpInstanceId = builder.ddpInstanceId;
        this.releaseCompleted = builder.releaseCompleted;
        this.assigneeIdMr = builder.assigneeIdMr;
        this.assigneeIdTissue = builder.assigneeIdTissue;
        this.lastChanged = builder.lastChanged;
        this.changedBy = builder.changedBy;
    }

    public static class Builder {

        private Optional<Integer> participantId = Optional.empty();
        private Optional<String> ddpParticipantId = Optional.empty();
        private Optional<Long> lastVersion = Optional.empty();
        private Optional<String> lastVersionDate = Optional.empty();
        private int ddpInstanceId;
        private Optional<Boolean> releaseCompleted = Optional.empty();
        private Optional<Integer> assigneeIdMr = Optional.empty();
        private Optional<Integer> assigneeIdTissue = Optional.empty();
        private long lastChanged;
        private Optional<String> changedBy = Optional.empty();

        public Builder(int ddpInstanceId, long lastChanged) {
            this.ddpInstanceId = ddpInstanceId;
            this.lastChanged = lastChanged;
        }

        public Builder withParticipantId(int participantId) {
            this.participantId = Optional.of(participantId);
            return this;
        }

        public Builder withDdpParticipantId(String ddpParticipantId) {
            this.ddpParticipantId = Optional.ofNullable(ddpParticipantId);
            return this;
        }

        public Builder withLastVersion(long lastVersion) {
            this.lastVersion = Optional.of(lastVersion);
            return this;
        }

        public Builder withLastVersionDate(String lastVersionDate) {
            this.lastVersionDate = Optional.ofNullable(lastVersionDate);
            return this;
        }

        public Builder withDdpInstanceId(int ddpInstanceId) {
            this.ddpInstanceId = ddpInstanceId;
            return this;
        }

        public Builder withReleaseCompleted(boolean releaseCompleted) {
            this.releaseCompleted = Optional.of(releaseCompleted);
            return this;
        }

        public Builder withAssigneeIdMr(int assigneeIdMr) {
            this.assigneeIdMr = Optional.of(assigneeIdMr);
            return this;
        }

        public Builder withAssigneeIdTissue(int assigneeIdTissue) {
            this.assigneeIdTissue = Optional.of(assigneeIdTissue);
            return this;
        }

        public Builder withLastChanged(long lastChanged) {
            this.lastChanged = lastChanged;
            return this;
        }

        public Builder withChangedBy(String changedBy) {
            this.changedBy = Optional.ofNullable(changedBy);
            return this;
        }

        public ParticipantDto build() {
            return new ParticipantDto(this);
        }

    }

}
