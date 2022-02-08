package org.broadinstitute.ddp.json.dsm;

import java.util.Optional;
import javax.validation.constraints.NotBlank;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.dsm.DsmNotificationEventType;
import org.broadinstitute.ddp.model.dsm.KitReasonType;

public class DsmNotificationPayload {

    @NotBlank
    @SerializedName("eventType")
    private String eventType;

    @SerializedName("kitRequestId")
    private String kitRequestId;

    @SerializedName("kitReasonType")
    private KitReasonType kitReasonType;

    @SerializedName("eventData")
    private JsonElement eventData;

    // Unused properties.
    @SerializedName("eventInfo")
    private String eventInfo;
    @SerializedName("eventDate")
    private long eventDate;

    public DsmNotificationPayload(String eventType, String kitRequestId) {
        this(eventType, kitRequestId, KitReasonType.NORMAL);
    }

    public DsmNotificationPayload(String eventType, String kitRequestId, KitReasonType kitReasonType) {
        this.eventType = eventType;
        this.kitRequestId = kitRequestId;
        this.kitReasonType = kitReasonType;
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

    public String getKitRequestId() {
        return kitRequestId;
    }

    public KitReasonType getKitReasonType() {
        return kitReasonType;
    }

    public JsonElement getEventData() {
        return eventData;
    }

    public void setEventData(JsonElement eventData) {
        this.eventData = eventData;
    }
}
