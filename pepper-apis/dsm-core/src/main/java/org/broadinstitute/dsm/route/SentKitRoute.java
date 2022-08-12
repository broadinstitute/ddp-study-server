package org.broadinstitute.dsm.route;

import org.broadinstitute.dsm.db.dao.kit.KitDaoImpl;
import org.broadinstitute.dsm.model.kit.KitSentUseCase;
import org.broadinstitute.dsm.statics.RoutePath;

public class SentKitRoute extends KitStatusChangeRoute {

    public SentKitRoute() {
        super(null);
    }

    @Override
    protected void processRequest() {
        KitSentUseCase kitSentUseCase = new KitSentUseCase(kitPayload, new KitDaoImpl());
        scanErrorList.addAll(kitSentUseCase.get());
    }
}
