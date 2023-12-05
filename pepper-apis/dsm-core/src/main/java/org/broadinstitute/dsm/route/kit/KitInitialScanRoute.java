package org.broadinstitute.dsm.route.kit;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.model.kit.KitInitialScanUseCase;
import org.broadinstitute.dsm.model.kit.ScanResult;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

public class KitInitialScanRoute extends KitStatusChangeRoute {

    public KitInitialScanRoute() {
        super(null);
    }

    @Override
    protected List<ScanResult> processRequest(KitPayload kitPayload) {
        KitInitialScanUseCase kitInitialScanUseCase = new KitInitialScanUseCase(kitPayload, new KitDao());
        return kitInitialScanUseCase.get();
    }

    @Override
    protected List<InitialScanPayload> getScanPayloads(String requestBody) {
        return ObjectMapperSingleton.readValue(requestBody, new TypeReference<List<InitialScanPayload>>() {
        });
    }
}
