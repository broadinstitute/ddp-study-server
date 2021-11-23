package org.broadinstitute.ddp.migration;

import java.time.Instant;

import com.google.gson.JsonObject;

public class SurveyWrapper extends ObjectWrapper {

    SurveyWrapper(JsonObject inner) {
        super(inner);
    }

    public Instant getCreated() {
        var value = getString("ddp_created");
        return value == null ? null : Instant.parse(value);
    }

    public Instant getLastUpdated() {
        var value = getString("ddp_lastupdated");
        return value == null ? null : Instant.parse(value);
    }

    public Instant getFirstCompleted() {
        var value = getString("ddp_firstcompleted");
        return value == null ? null : Instant.parse(value);
    }

    public Long getSubmissionId() {
        return getLong("datstat.submissionid");
    }

    public String getSessionId() {
        return getString("datstat.sessionid");
    }

    public String getVersion() {
        return getString("datstat.version");
    }
}
