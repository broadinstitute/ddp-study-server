package org.broadinstitute.dsm.db.dto.queue;

public class EventDto {

    private int eventId;
    private long eventDateCreated;
    private String eventType;
    private int ddpInstanceId;
    private int dsmKitRequestId;
    private String ddpParticipantId;
    private Boolean eventTriggered;

    private EventDto(Builder builder) {
        this.eventId = builder.eventId;
        this.eventDateCreated = builder.eventDateCreated;
        this.eventType = builder.eventType;
        this.ddpInstanceId = builder.ddpInstanceId;
        this.dsmKitRequestId = builder.dsmKitRequestId;
        this.ddpParticipantId = builder.ddpParticipantId;
        this.eventTriggered = builder.eventTriggered;
    }

    public static class Builder {

        private int eventId;
        private long eventDateCreated;
        private String eventType;
        private int ddpInstanceId;
        private int dsmKitRequestId;
        private String ddpParticipantId;
        private Boolean eventTriggered;

        public Builder(int ddpInstanceId) {
            this.ddpInstanceId = ddpInstanceId;
        }

        public Builder withEventId(int eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder withEventDateCreated(long eventDateCreated) {
            this.eventDateCreated = eventDateCreated;
            return this;
        }

        public Builder withEventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder withDsmKitRequestId(int dsmKitRequestId) {
            this.dsmKitRequestId = dsmKitRequestId;
            return this;
        }

        public Builder withDdpParticipantId(String ddpParticipantId) {
            this.ddpParticipantId = ddpParticipantId;
            return this;
        }

        public Builder withEventTriggered(Boolean eventTriggered) {
            this.eventTriggered = eventTriggered;
            return this;
        }

        public EventDto build() {
            return new EventDto(this);
        }
    }
}
