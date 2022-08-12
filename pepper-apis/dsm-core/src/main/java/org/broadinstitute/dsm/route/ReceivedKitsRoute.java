package org.broadinstitute.dsm.route;

import lombok.NonNull;
import org.broadinstitute.dsm.db.dao.kit.KitDaoImpl;
import org.broadinstitute.dsm.model.kit.KitSentUseCase;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.NotificationUtil;

public class ReceivedKitsRoute extends KitStatusChangeRoute {
    public ReceivedKitsRoute(@NonNull NotificationUtil notificationUtil) {
        super(notificationUtil);
    }

    @Override
    protected void processRequest() {
        KitSentUseCase kitSentUseCase = new KitSentUseCase(kitPayload, new KitDaoImpl());
        scanErrorList.addAll(kitSentUseCase.get());
    }
}
