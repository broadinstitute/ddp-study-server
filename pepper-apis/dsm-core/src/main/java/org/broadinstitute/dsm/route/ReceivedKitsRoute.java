package org.broadinstitute.dsm.route;

import lombok.NonNull;
import org.broadinstitute.dsm.db.dao.kit.KitDaoImpl;
import org.broadinstitute.dsm.model.kit.KitReceivedUseCase;
import org.broadinstitute.dsm.util.NotificationUtil;

public class ReceivedKitsRoute extends KitStatusChangeRoute {
    public ReceivedKitsRoute(@NonNull NotificationUtil notificationUtil) {
        super(notificationUtil);
    }

    @Override
    protected void processRequest() {
        KitReceivedUseCase kitReceivedUseCase = new KitReceivedUseCase(kitPayload, new KitDaoImpl(), notificationUtil);
        scanErrorList.addAll(kitReceivedUseCase.get());
    }
}
