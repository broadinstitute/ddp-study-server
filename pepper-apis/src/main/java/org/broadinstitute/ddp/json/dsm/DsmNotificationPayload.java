package org.broadinstitute.ddp.json.dsm;

import java.util.Optional;
import javax.annotation.Nullable;
import javax.validation.constraints.NotBlank;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.DsmNotificationEventType;

public class DsmNotificationPayload {

    @SerializedName("eventInfo")
    private String eventInfo;

    @NotBlank
    @SerializedName("eventType")
    private String eventType;

    @SerializedName("eventDate")
    private long eventDate;

    public DsmNotificationPayload(@Nullable String eventInfo, String eventType, long eventDate) {
        this.eventInfo = eventInfo;
        this.eventType = eventType;
        this.eventDate = eventDate;
    }

    public String getEventType() {
        return eventType;
    }

    public Optional<DsmNotificationEventType> parseEventTypeCode() {
        try {
            return Optional.of(DsmNotificationEventType.valueOf(eventType.replace("-", "_").toUpperCase()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
