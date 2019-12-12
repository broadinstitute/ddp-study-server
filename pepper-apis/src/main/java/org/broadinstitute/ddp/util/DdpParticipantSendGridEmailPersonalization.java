package org.broadinstitute.ddp.util;

import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_BASE_WEB_URL;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_LINK;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_PARTICIPANT_FIRST_NAME;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_PARTICIPANT_LAST_NAME;

import java.util.HashMap;
import java.util.Map;

public class DdpParticipantSendGridEmailPersonalization {
    private Map<String, String> keyToValue = new HashMap<>();

    public DdpParticipantSendGridEmailPersonalization setParticipantFirstName(String firstName) {
        keyToValue.put(DDP_PARTICIPANT_FIRST_NAME, firstName);
        return this;
    }

    public DdpParticipantSendGridEmailPersonalization setParticipantLastName(String lastName) {
        keyToValue.put(DDP_PARTICIPANT_LAST_NAME, lastName);
        return this;
    }

    public DdpParticipantSendGridEmailPersonalization setLinkValue(String urlValue) {
        keyToValue.put(DDP_LINK, urlValue);
        return this;
    }

    public DdpParticipantSendGridEmailPersonalization setBaseWebUrl(String baseWebUrl) {
        keyToValue.put(DDP_BASE_WEB_URL, baseWebUrl);
        return this;
    }

    public Map<String, String> toMap() {
        return new HashMap<>(this.keyToValue);
    }
}


