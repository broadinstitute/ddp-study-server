package org.broadinstitute.lddp.handlers.util;

import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.lddp.util.CheckValidity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class Event implements CheckValidity
{
    private static final Logger logger = LoggerFactory.getLogger(Event.class);

    private static final String LOG_PREFIX = "EVENT - ";

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