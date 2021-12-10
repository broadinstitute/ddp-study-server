package org.broadinstitute.dsm.db.dto.settings;

import lombok.Getter;

@Getter
public class EventTypeDto {

    private int eventTypeId;
    private String ddpInstanceId;
    private String instanceName;
    private String baseUrl;
    private Boolean auth0Token;
    private String eventName;
    private String eventDescription;
    private int kitTypeId;
    private String eventType;
    private int hours;

    private EventTypeDto(Builder builder) {
        this.eventTypeId = builder.eventTypeId;
        this.ddpInstanceId = builder.ddpInstanceId;
        this.instanceName = builder.instanceName;
        this.baseUrl = builder.baseUrl;
        this.auth0Token = builder.auth0Token;
        this.eventName = builder.eventName;
        this.eventDescription = builder.eventDescription;
        this.kitTypeId = builder.kitTypeId;
        this.eventType = builder.eventType;
        this.hours = builder.hours;
    }

    public static class Builder {

        private int eventTypeId;
        private String ddpInstanceId;
        private String instanceName;
        private String baseUrl;
        private Boolean auth0Token;
        private String eventName;
        private String eventDescription;
        private int kitTypeId;
        private String eventType;
        private int hours;

        public Builder(String ddpInstanceId) {
            this.ddpInstanceId = ddpInstanceId;
        }

        public Builder withEventTypeId(int eventTypeId) {
            this.eventTypeId = eventTypeId;
            return this;
        }

        public Builder withInstanceName(String instanceName) {
            this.instanceName = instanceName;
            return this;
        }

        public Builder withBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder withAuth0Token(Boolean auth0Token) {
            this.auth0Token = auth0Token;
            return this;
        }

        public Builder withEventName(String eventName) {
            this.eventName = eventName;
            return this;
        }

        public Builder withEventDescription(String eventDescription) {
            this.eventDescription = eventDescription;
            return this;
        }

        public Builder withKitTypeId(int kitTypeId) {
            this.kitTypeId = kitTypeId;
            return this;
        }

        public Builder withEventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder withHours(int hours) {
            this.hours = hours;
            return this;
        }

        public EventTypeDto build() {
            return new EventTypeDto(this);
        }
    }
}
