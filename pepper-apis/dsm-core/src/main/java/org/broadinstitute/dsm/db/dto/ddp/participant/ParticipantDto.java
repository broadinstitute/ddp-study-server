package org.broadinstitute.dsm.db.dto.ddp.participant;

import java.util.Optional;

import lombok.Getter;
import org.broadinstitute.dsm.exception.DsmInternalError;

@Getter
public class ParticipantDto implements Cloneable {

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

    public void setParticipantId(int participantId) {
        this.participantId = Optional.of(participantId);
    }

    public int getParticipantIdOrThrow() {
        return getParticipantId().orElseThrow(() -> new DsmInternalError(String.format("Participant ID cannot "
                + " be null for DDP participant ID %s and instance ID %d", getDdpParticipantId().orElse("null"), ddpInstanceId)));
    }

    /**
     * Returns participant ID that is expected to be set
     * Note: This is designed to replace getParticipantIdOrThrow()
     */
    public int getRequiredParticipantId() {
        return getParticipantIdOrThrow();
    }

    public String getDdpParticipantIdOrThrow() {
        return getDdpParticipantId().orElseThrow(() -> new DsmInternalError(String.format("DDP participant ID cannot"
                + " be null for participant ID %d and instance ID %d", getParticipantId().orElse(-1), ddpInstanceId)));
    }

    /**
     * Returns DDP participant ID that is expected to be set
     * Note: This is designed to replace getDdpParticipantIdOrThrow()
     */
    public String getRequiredDdpParticipantId() {
        return getDdpParticipantIdOrThrow();
    }

    public void setDdpInstanceId(int ddpInstanceId) {
        this.ddpInstanceId = ddpInstanceId;
    }

    public void setAssigneeIdMr(Integer assigneeIdMr) {
        this.assigneeIdMr = Optional.ofNullable(assigneeIdMr);
    }

    public void setAssigneeIdTissue(Integer assigneeIdTissue) {
        this.assigneeIdTissue = Optional.ofNullable(assigneeIdTissue);
    }

    @Override
    public ParticipantDto clone() {
        try {
            return (ParticipantDto) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
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

        public Builder() {}

        public Builder withParticipantId(int participantId) {
            this.participantId = Optional.of(participantId);
            return this;
        }

        public Builder withDdpParticipantId(String ddpParticipantId) {
            this.ddpParticipantId = Optional.ofNullable(ddpParticipantId);
            return this;
        }

        public Builder withLastVersion(Long lastVersion) {
            this.lastVersion = Optional.ofNullable(lastVersion);
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

        public Builder withReleaseCompleted(Boolean releaseCompleted) {
            this.releaseCompleted = Optional.ofNullable(releaseCompleted);
            return this;
        }

        public Builder withAssigneeIdMr(Integer assigneeIdMr) {
            this.assigneeIdMr = Optional.ofNullable(assigneeIdMr);
            return this;
        }

        public Builder withAssigneeIdTissue(Integer assigneeIdTissue) {
            this.assigneeIdTissue = Optional.ofNullable(assigneeIdTissue);
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
