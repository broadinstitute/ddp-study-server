package org.broadinstitute.ddp.db.dto;

public class EventActionDto {
    private long id;
    private Long messageDestinationId;

    public EventActionDto(long id, Long messageDestinationId) {
        this.id = id;
        this.messageDestinationId = messageDestinationId;
    }

    public long getId() {
        return id;
    }

    public Long getMessageDestinationId() {
        return messageDestinationId;
    }
}
