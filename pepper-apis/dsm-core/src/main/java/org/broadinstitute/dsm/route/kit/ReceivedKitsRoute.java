package org.broadinstitute.dsm.route.kit;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.NonNull;
import org.broadinstitute.dsm.db.dao.kit.KitDaoImpl;
import org.broadinstitute.dsm.model.kit.KitReceivedUseCase;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

public class ReceivedKitsRoute extends KitStatusChangeRoute {
    public ReceivedKitsRoute(@NonNull NotificationUtil notificationUtil) {
        super(notificationUtil);
    }

    @Override
    protected void processRequest() {
        KitReceivedUseCase kitReceivedUseCase = new KitReceivedUseCase(kitPayload, new KitDaoImpl(), notificationUtil);
        scanErrorList.addAll(kitReceivedUseCase.get());
    }

    @Override
    protected List<? extends ScanPayload> getScanPayloads(String requestBody) {
        return ObjectMapperSingleton.readValue(requestBody, new TypeReference<List<SentAndFinalScanPayload>>() {});
    }
}
