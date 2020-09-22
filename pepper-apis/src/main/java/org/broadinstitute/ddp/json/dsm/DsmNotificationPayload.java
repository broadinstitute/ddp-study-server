package org.broadinstitute.ddp.json.dsm;

import java.time.Instant;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.validation.constraints.NotBlank;

import com.google.gson.JsonElement;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.DsmNotificationEventType;
import org.broadinstitute.ddp.transformers.InstantToIsoDateTimeUtcStrAdapter;

public class DsmNotificationPayload {

    @SerializedName("kitRequestId")
    private String kitRequestGuid;

    @SerializedName("eventTime")
    @JsonAdapter(InstantToIsoDateTimeUtcStrAdapter.class)
    private Instant eventTime;

    @SerializedName("eventInfo")
    private String eventInfo;

    @NotBlank
    @SerializedName("eventType")
    private String eventType;

    @SerializedName("eventDate")
    private long eventDate;

    @SerializedName("eventData")
    private JsonElement eventData;

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

    public JsonElement getEventData() {
        return eventData;
    }

    public void setEventData(JsonElement eventData) {
        this.eventData = eventData;
    }

    public String getKitRequestGuid() {
        return kitRequestGuid;
    }

    public void setKitRequestGuid(String kitRequestGuid) {
        this.kitRequestGuid = kitRequestGuid;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }
}
