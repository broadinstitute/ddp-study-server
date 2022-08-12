package org.broadinstitute.dsm.route;

import org.broadinstitute.dsm.db.dao.kit.KitDaoImpl;
import org.broadinstitute.dsm.model.kit.KitFinalScanUseCase;

public class KitFinalScanRoute extends KitStatusChangeRoute {
    public KitFinalScanRoute() {
        super(null);
    }

    @Override
    protected void processRequest() {
        KitFinalScanUseCase kitFinalScanUseCase = new KitFinalScanUseCase(kitPayload, new KitDaoImpl());
        scanErrorList.addAll(kitFinalScanUseCase.get());
    }
}
