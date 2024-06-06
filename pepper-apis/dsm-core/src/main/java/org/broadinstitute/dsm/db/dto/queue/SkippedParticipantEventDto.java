package org.broadinstitute.dsm.db.dto.queue;

import lombok.Data;

@Data
public class SkippedParticipantEventDto {
    /**
     * The event_type in this table is actually the event_name from the event_type table
     */
    private final String participantId;
    private final String eventType;
    private final String user;
    private final long date;
    private String shortId;

    public SkippedParticipantEventDto(String participantId, String eventType, String user, long date) {
        this.participantId = participantId;
        this.eventType = eventType;
        this.user = user;
        this.date = date;
    }
}
