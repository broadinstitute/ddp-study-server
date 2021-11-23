package org.broadinstitute.lddp.handlers.util;

import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.lddp.util.CheckValidity;

@Data
public class Event implements CheckValidity
{

    private String eventInfo;
    private String eventType;
    private long eventDate;

    public boolean isValid()
    {
        return (StringUtils.isNotBlank(eventType));
    }

    public Event() {

    }

    public Event(String eventInfo, @NonNull String eventType, @NonNull Long eventDate) {
        this.eventInfo = eventInfo;
        this.eventType = eventType;
        this.eventDate = eventDate;
    }
}