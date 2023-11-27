package org.broadinstitute.ddp.cf.json;

import com.google.gson.annotations.SerializedName;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@SuppressWarnings("unused")
public class OutgoingRequest {

    @SerializedName("email")
    private final String email;

    @SerializedName("start")
    private String start;

    @SerializedName("end")
    private String end;

    @SerializedName("is_active")
    private final Boolean isActive;

    @SerializedName("cohort")
    private final String sleeplogCohort;

    public OutgoingRequest(String email, Boolean isActive, String sleeplogCohort, boolean addDates) {
        this.email = email;
        if (addDates) {
            this.start = DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDateTime.now());
            this.end = DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDateTime.now().plusWeeks(6));
        }
        this.isActive = isActive;
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

    @Override
    public String toString() {
        return "?cohort=" + getSleeplogCohort() + "&email=" + getEmail();
    }
}
