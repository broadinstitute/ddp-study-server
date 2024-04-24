package org.broadinstitute.dsm.model.at;

import java.util.Optional;

import org.broadinstitute.dsm.db.dto.settings.EventTypeDto;
import org.broadinstitute.dsm.model.KitDDPNotification;
import org.broadinstitute.dsm.security.Auth0Util;



public class DSSTriggerInfo {
    private String eventType;
    private KitDDPNotification kitDDPNotification;
    private Optional<EventTypeDto> eventTypes;
    private String ddpParticipantId;
    private Auth0Util auth0Util;
}
