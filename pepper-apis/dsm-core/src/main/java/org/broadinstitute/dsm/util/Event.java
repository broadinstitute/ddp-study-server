package org.broadinstitute.dsm.util;

import org.apache.commons.lang3.StringUtils;

public class Event {
    private String normal = "NORMAL";
    private String kitReasonType;
    private String eventInfo;
    private String ddpParticipantId;
    private String eventType;
    private long eventDate;

    /**
     * Creates a new event to be inserted into the database.
     * eventInfo could be ddpParticipantId for participant events, or kitRequestId for kit events.
     * **/
    public Event(String ddpParticipantId, String eventType, long eventDate, String kitReasonType, String eventInfo) {
        this.ddpParticipantId = ddpParticipantId;
        this.eventType = eventType;
        this.eventDate = eventDate;
        if (StringUtils.isBlank(kitReasonType)) {
            kitReasonType = normal;
        }
        this.kitReasonType = kitReasonType;
        this.eventInfo = eventInfo;
    }
}