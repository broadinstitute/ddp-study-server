package org.broadinstitute.ddp.json.dsm;

import com.google.gson.annotations.SerializedName;

public class DsmNotificationEvent {

    @SerializedName(Fields.EVENT_INFO)
    private String eventInfo;
    @SerializedName(Fields.EVENT_TYPE)
    private String eventType;
    @SerializedName(Fields.EVENT_DATE)
    private long eventDate;

    public DsmNotificationEvent(
            String eventInfo,
            String eventType,
            long eventDate
    ) {
        this.eventInfo = eventInfo;
        this.eventType = eventType;
        this.eventDate = eventDate;
    }

    public String getEventType() {
        return eventType;
    }

    public static class Fields {
        public static final String EVENT_INFO = "eventInfo";
        public static final String EVENT_TYPE = "eventType";
        public static final String EVENT_DATE = "eventDate";
    }

}
