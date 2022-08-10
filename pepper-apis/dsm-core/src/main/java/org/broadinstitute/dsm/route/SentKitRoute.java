package org.broadinstitute.dsm.route;

import org.broadinstitute.dsm.model.kit.KitSentReceivedUseCase;
import org.broadinstitute.dsm.statics.RoutePath;

public class SentKitRoute extends KitStatusChangeRoute {

    public SentKitRoute() {
        super(null);
    }

    @Override
    protected void processRequest() {
        KitSentReceivedUseCase kitSentReceivedUseCase = new KitSentReceivedUseCase();
        updateKits(RoutePath.SENT_KIT_REQUEST, scans, currentTime, scanErrorList, userIdRequest, ddpInstanceDto);
    }
}
