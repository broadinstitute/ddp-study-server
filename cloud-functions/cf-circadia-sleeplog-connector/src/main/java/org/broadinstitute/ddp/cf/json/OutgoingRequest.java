package org.broadinstitute.ddp.cf.json;

import com.google.gson.annotations.SerializedName;

@SuppressWarnings("unused")
public class OutgoingRequest {

    @SerializedName("email")
    private final String email;

    @SerializedName("start")
    private final String start;

    @SerializedName("end")
    private final String end;

    @SerializedName("is_active")
    private final Boolean isActive;

    @SerializedName("cohort")
    private final String sleeplogCohort;

    public OutgoingRequest(IncomingRequest request, String sleeplogCohort) {
        this.email = request.getEmail();
        this.start = request.getStart();
        this.end = request.getEnd();
        this.isActive = request.getIsActive();
        this.sleeplogCohort = sleeplogCohort;
    }

    public String getEmail() {
        return email;
    }

    public String getStart() {
        return start;
    }

    public String getEnd() {
        return end;
    }

    public Boolean getActive() {
        return isActive;
    }

    public String getSleeplogCohort() {
        return sleeplogCohort;
    }
}
