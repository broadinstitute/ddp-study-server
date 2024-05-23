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
     * Creates a new event to be sent to DSS.
     * eventInfo could be ddpParticipantId for participant events, or ddpKitRequestId for kit events.
     * **/
    public Event(String ddpParticipantId, String eventType, long eventDate, String kitReasonType, String eventInfo) {
        this.ddpParticipantId = ddpParticipantId;
        this.eventType = eventType;
        this.eventDate = eventDate;
        this.kitReasonType = kitReasonType;
        if (StringUtils.isBlank(kitReasonType)) {
            this.kitReasonType = normal;
        }
        this.eventInfo = eventInfo;
    }
}
