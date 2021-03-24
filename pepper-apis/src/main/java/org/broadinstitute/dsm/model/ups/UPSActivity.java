package org.broadinstitute.dsm.model.ups;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import lombok.Data;

@Data
public class UPSActivity {
    UPSLocation location;
    UPSStatus status;
    String date;
    String time;

    public UPSActivity() {}

    public UPSActivity(UPSStatus status, String date, String time) {
        this.status = status;
        this.date = date;
        this.time = time;
    }

    public String getDateTimeString() {
        return date + " " + time;
    }

    /**
     * Returns the instant of this event.  Assumes New York
     * time zone!
     */
    public Instant getInstant() {
        Instant eventTime = null;
        String dateTime = getDateTimeString();
        if (dateTime != null) {
            eventTime = DateTimeFormatter.ofPattern("yyyyMMdd HHmmss").withZone(ZoneId.of("America/New_York")).parse(dateTime, Instant::from);
        }
        return eventTime;
    }

    /**
     * Convenience method for {@link UPSStatus#isOnItsWay()}
     */
    public boolean isOnItsWay() {
        return status.isOnItsWay();
    }

    /**
     * Convenience method for {@link UPSStatus#isPickup()}
     */
    public boolean isPickup() {
        return status.isPickup();
    }
}
