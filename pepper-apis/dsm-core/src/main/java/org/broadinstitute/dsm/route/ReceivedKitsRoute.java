package org.broadinstitute.dsm.route;

import lombok.NonNull;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.NotificationUtil;

public class ReceivedKitsRoute extends KitStatusChangeRoute {
    public ReceivedKitsRoute(@NonNull NotificationUtil notificationUtil) {
        super(notificationUtil);
    }

    @Override
    protected void processRequest() {
        updateKits(RoutePath.RECEIVED_KIT_REQUEST, scans, currentTime, scanErrorList, userIdRequest, ddpInstanceDto);
    }
}
