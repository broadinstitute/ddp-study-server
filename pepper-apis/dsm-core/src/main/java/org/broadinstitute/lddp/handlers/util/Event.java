package org.broadinstitute.lddp.handlers.util;

import lombok.Data;
import lombok.NonNull;

@Data
public class Event {

    private String eventInfo;
    private String eventType;
    private long eventDate;

    public Event() {
    }

    public Event(String eventInfo, @NonNull String eventType, @NonNull Long eventDate) {
        this.eventInfo = eventInfo;
        this.eventType = eventType;
        this.eventDate = eventDate;
    }
}
