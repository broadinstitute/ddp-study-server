package org.broadinstitute.dsm.route;

import org.broadinstitute.dsm.model.kit.KitFinalScanUseCase;
import org.broadinstitute.dsm.statics.RoutePath;

public class KitFinalScanRoute extends KitStatusChangeRoute {
    public KitFinalScanRoute() {
        super(null);
    }

    @Override
    protected void processRequest() {
        KitFinalScanUseCase kitFinalScanUseCase = new KitFinalScanUseCase();
        updateKits(RoutePath.FINAL_SCAN_REQUEST, scans, currentTime, scanErrorList, userIdRequest, ddpInstanceDto);
    }
}
