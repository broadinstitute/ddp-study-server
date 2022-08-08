package org.broadinstitute.dsm.route;

import org.broadinstitute.dsm.statics.RoutePath;

public class KitTrackingScanRoute extends KitStatusChangeRoute {
    public KitTrackingScanRoute() {
        super(null);
    }

    @Override
    protected void processRequest() {
        updateKits(RoutePath.TRACKING_SCAN_REQUEST, scans, currentTime, scanErrorList, userIdRequest, ddpInstanceDto);
    }
}
