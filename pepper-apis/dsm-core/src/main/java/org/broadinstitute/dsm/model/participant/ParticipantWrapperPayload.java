package org.broadinstitute.dsm.model.participant;

import java.util.Map;
import java.util.Optional;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.sort.SortBy;

public class ParticipantWrapperPayload {

    private DDPInstanceDto ddpInstanceDto;
    private Map<String, String> filter;
    private int userId;
    private int from;
    private int to;
    private SortBy sortBy;
    private boolean showMedicalRecordRequestTracking;

    public Optional<DDPInstanceDto> getDdpInstanceDto() {
        return Optional.ofNullable(ddpInstanceDto);
    }

    public Optional<Map<String, String>> getFilter() {
        return Optional.ofNullable(filter);
    }

    public Optional<SortBy> getSortBy() {
        return Optional.ofNullable(sortBy);
    }

    public int getUserId() {
        return userId;
    }

    public int getFrom() {
        return this.from;
    }

    public int getTo() {
        return this.to;
    }

    public boolean isShowMedicalRecordRequestTracking() {
        return this.showMedicalRecordRequestTracking;
    }

    private ParticipantWrapperPayload(Builder builder) {
        this.ddpInstanceDto = builder.ddpInstanceDto;
        this.filter = builder.filter;
        this.userId = builder.userId;
        this.from = builder.from;
        this.to = builder.to;
        this.sortBy = builder.sortBy;
        this.showMedicalRecordRequestTracking = builder.showMedicalRecordRequestTracking;
    }

    public static class Builder {

        public int from;
        public int to;
        public SortBy sortBy;
        private DDPInstanceDto ddpInstanceDto;
        private Map<String, String> filter;
        private int userId;
        private boolean showMedicalRecordRequestTracking;

        public Builder withDdpInstanceDto(DDPInstanceDto ddpInstanceDto) {
            this.ddpInstanceDto = ddpInstanceDto;
            return this;
        }

        public Builder withFilter(Map<String, String> filter) {
            this.filter = filter;
            return this;
        }

        public Builder withUserId(int userId) {
            this.userId = userId;
            return this;
        }

        public Builder withFrom(int from) {
            this.from = from;
            return this;
        }

        public Builder withTo(int to) {
            this.to = to;
            return this;
        }

        public Builder withSortBy(SortBy sortBy) {
            this.sortBy = sortBy;
            return this;
        }

        public Builder withShowMedicalRecordRequestTracking(boolean showMedicalRecordRequestTracking) {
            this.showMedicalRecordRequestTracking = showMedicalRecordRequestTracking;
            return this;
        }

        public ParticipantWrapperPayload build() {
            return new ParticipantWrapperPayload(this);
        }
    }

}
