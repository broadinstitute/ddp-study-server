package org.broadinstitute.dsm.route;

import org.broadinstitute.dsm.db.dao.kit.KitDaoImpl;
import org.broadinstitute.dsm.model.kit.KitTrackingScanUseCase;
import org.broadinstitute.dsm.statics.RoutePath;

public class KitTrackingScanRoute extends KitStatusChangeRoute {
    public KitTrackingScanRoute() {
        super(null);
    }

    @Override
    protected void processRequest() {
        KitTrackingScanUseCase kitTrackingScanUseCase = new KitTrackingScanUseCase(kitPayload, new KitDaoImpl());
        scanErrorList.addAll(kitTrackingScanUseCase.get());
    }
}
