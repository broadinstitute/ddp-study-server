package org.broadinstitute.dsm.util;

import lombok.NonNull;

public class ParticipantUtil {

    public static final String DDP_PARTICIPANT_ID = "ddpParticipantId";

    public static boolean isHruid(@NonNull String participantId) {
        final String hruidCheck = "^P\\w{5}$";
        return participantId.matches(hruidCheck);
    }
}
