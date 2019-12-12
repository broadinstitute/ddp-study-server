package org.broadinstitute.ddp.export.json.structured;

import com.google.gson.annotations.SerializedName;

public class ParticipantProfile {

    @SerializedName("guid")
    private String guid;
    @SerializedName("hruid")
    private String hruid;
    @SerializedName("legacyAltPid")
    private String legacyAltPid;
    @SerializedName("legacyShortId")
    private String legacyShortId;
    @SerializedName("firstName")
    private String firstName;
    @SerializedName("lastName")
    private String lastName;
    @SerializedName("email")
    private String email;
    @SerializedName("doNotContact")
    private boolean doNotContact;
    @SerializedName("createdAt")
    private long createdAt;

    public ParticipantProfile(
            String firstName,
            String lastName,
            String guid,
            String hruid,
            String legacyAltPid,
            String legacyShortId,
            String email,
            Boolean doNotContact,
            long createdAt
    ) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.guid = guid;
        this.hruid = hruid;
        this.legacyAltPid = legacyAltPid;
        this.legacyShortId = legacyShortId;
        this.email = email;
        this.doNotContact = (doNotContact == null ? false : doNotContact);
        this.createdAt = createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String firstName;
        private String lastName;
        private String guid;
        private String hruid;
        private String legacyAltPid;
        private String legacyShortId;
        private String email;
        private Boolean doNotContact;
        private long createdAt;

        public ParticipantProfile build() {
            return new ParticipantProfile(
                    firstName,
                    lastName,
                    guid,
                    hruid,
                    legacyAltPid,
                    legacyShortId,
                    email,
                    doNotContact,
                    createdAt
            );
        }

        public Builder setFirstName(String firstName) {
            this.firstName = firstName;
            return this;
        }

        public Builder setLastName(String lastName) {
            this.lastName = lastName;
            return this;
        }

        public Builder setGuid(String guid) {
            this.guid = guid;
            return this;
        }

        public Builder setHruid(String hruid) {
            this.hruid = hruid;
            return this;
        }

        public Builder setLegacyAltPid(String legacyAltPid) {
            this.legacyAltPid = legacyAltPid;
            return this;
        }

        public Builder setLegacyShortId(String legacyShortId) {
            this.legacyShortId = legacyShortId;
            return this;
        }

        public Builder setEmail(String email) {
            this.email = email;
            return this;
        }

        public Builder setDoNotContact(Boolean doNotContact) {
            this.doNotContact = doNotContact;
            return this;
        }

        public Builder setCreatedAt(long createdAt) {
            this.createdAt = createdAt;
            return this;
        }
    }
}
