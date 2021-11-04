package org.broadinstitute.dsm.model.ups;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Data
public class UPSActivity {
    UPSLocation location;
    UPSStatus status;
    String date;
    String time;
    String dateTime;
    String activityId;
    String packageId;
    String locationString;


    public UPSActivity(String locationString, UPSStatus status, String date, String time, String activityId,
                       String packageId, String dateTime) {
        this.locationString = locationString;
        this.status = status;
        this.date = date;
        this.time = time;
        this.activityId = activityId;
        this.packageId = packageId;
        this.dateTime = dateTime;
    }

    public UPSActivity(UPSLocation location, UPSStatus status, String date, String time, String activityId,
                       String packageId, String dateTime) {
        this.location = location;
        this.status = status;
        this.date = date;
        this.time = time;
        this.activityId = activityId;
        this.packageId = packageId;
        this.dateTime = dateTime;
    }

    public String getDateTimeString() {
        if (StringUtils.isBlank(this.getTime()) && StringUtils.isBlank(this.getDate())) {
            return null;
        }
        if (StringUtils.isBlank(this.getTime())) {
            this.setTime("000000");
        }
        else if (StringUtils.isBlank(this.getDate())) {
            return null;
        }
        return this.getDate() + " " + this.getTime();
    }

    public String getSQLDateTimeString() {
        Instant activityInstant = this.getInstant();
        DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("America/New_York"));
        String activityDateTime = DATE_TIME_FORMATTER.format(activityInstant);
        return activityDateTime;
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
        else if (StringUtils.isNotBlank(this.getDateTime())){
            dateTime = this.getDateTime();
            eventTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("America/New_York")).parse(dateTime, Instant::from);
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
