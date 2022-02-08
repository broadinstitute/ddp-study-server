package org.broadinstitute.dsm.util;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.lddp.handlers.util.Event;

public class KitEvent extends Event {
    public String NORMAL = "NORMAL";
    private String kitReasonType;
    private String kitRequestId;

    public KitEvent(String eventInfo, String eventType, long eventDate, String kitReasonType, String kitRequestId) {
        super(eventInfo, eventType, eventDate);
        if (StringUtils.isBlank(kitReasonType)) {
            kitReasonType = NORMAL;
        }
        this.kitReasonType = kitReasonType;
        this.kitRequestId = kitRequestId;
    }
}
