package org.broadinstitute.dsm.route.kit;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.model.kit.KitFinalScanUseCase;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

public class KitFinalScanRoute extends KitStatusChangeRoute {
    public KitFinalScanRoute() {
        super(null);
    }

    @Override
    protected void processRequest() {
        KitFinalScanUseCase kitFinalScanUseCase = new KitFinalScanUseCase(kitPayload, new KitDao());
        scanResultList.addAll(kitFinalScanUseCase.get());
    }

    @Override
    protected List<SentAndFinalScanPayload> getScanPayloads(String requestBody) {
        return ObjectMapperSingleton.readValue(requestBody, new TypeReference<List<SentAndFinalScanPayload>>() {});
    }
}
