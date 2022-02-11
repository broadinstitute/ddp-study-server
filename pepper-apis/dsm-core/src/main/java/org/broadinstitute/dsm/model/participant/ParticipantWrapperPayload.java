package org.broadinstitute.dsm.model.participant;

import java.util.Map;
import java.util.Optional;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;


public class ParticipantWrapperPayload {

    private DDPInstanceDto ddpInstanceDto;
    private Map<String, String> filter;
    private int userId;
    private int from;
    private int to;

    public Optional<DDPInstanceDto> getDdpInstanceDto() {
        return Optional.ofNullable(ddpInstanceDto);
    }

    public Optional<Map<String, String>> getFilter() {
        return Optional.ofNullable(filter);
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

    private ParticipantWrapperPayload(Builder builder) {
        this.ddpInstanceDto = builder.ddpInstanceDto;
        this.filter = builder.filter;
        this.userId = builder.userId;
        this.from = builder.from;
        this.to = builder.to;
    }

    public static class Builder {

        public int from;
        public int to;
        private DDPInstanceDto ddpInstanceDto;
        private Map<String, String> filter;
        private int userId;

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

        public ParticipantWrapperPayload build() {
            return new ParticipantWrapperPayload(this);
        }
    }

}
