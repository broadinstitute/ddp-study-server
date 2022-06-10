package org.broadinstitute.dsm.db.dto.ddp.participant;

import java.util.Optional;

import lombok.Getter;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.pubsub.study.osteo.DataCopyingException;

import static org.broadinstitute.dsm.statics.DBConstants.*;
import static org.broadinstitute.dsm.util.SystemUtil.SYSTEM;

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

    public static ParticipantDto copy(int newDdpInstanceId, ParticipantDto that) {
        Integer assigneeIdMr = Util.orElseNull(that.getAssigneeIdMr(), 0);
        Integer assigneeIdTissue = Util.orElseNull(that.getAssigneeIdTissue(), 0);
        return new ParticipantDto.Builder()
                .withDdpParticipantId(that.getDdpParticipantId().orElseThrow(DataCopyingException.withMessage(DDP_INSTANCE_ID)))
                .withLastVersion(that.getLastVersion().orElseThrow())
                .withLastVersionDate(that.getLastVersionDate().orElse(null))
                .withDdpInstanceId(newDdpInstanceId)
                .withReleaseCompleted(that.getReleaseCompleted().orElse(null))
                .withAssigneeIdMr(assigneeIdMr)
                .withAssigneeIdTissue(assigneeIdTissue)
                .withLastChanged(that.getLastChanged())
                .withChangedBy(that.getChangedBy().orElse(SYSTEM))
                .build();
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
