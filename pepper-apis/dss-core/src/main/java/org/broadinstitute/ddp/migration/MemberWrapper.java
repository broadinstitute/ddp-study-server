package org.broadinstitute.ddp.migration;

import java.time.Instant;

import com.google.gson.JsonObject;

public class MemberWrapper extends ObjectWrapper {

    MemberWrapper(JsonObject inner) {
        super(inner);
    }

    public Instant getCreated() {
        var value = getString("ddp_created");
        return value == null ? null : Instant.parse(value);
    }

    public String getFamilyId() {
        return getString("family_id");
    }

    public String getMemberType() {
        return getString("member_type");
    }

    public String getShortId() {
        return getString("ddp_participant_shortid");
    }

    public String getAltPid() {
        return getString("datstat_altpid");
    }

    public String getEmail() {
        return getString("datstat_email");
    }

    public String getFirstName() {
        return getString("datstat_firstname");
    }

    public String getLastName() {
        return getString("datstat_lastname");
    }

    public String getInactiveReason() {
        return getString("inactive_reason");
    }

    public Instant getLastModified() {
        var value = getString("datstat_lastmodified");
        return value == null ? null : Instant.parse(value);
    }
}
