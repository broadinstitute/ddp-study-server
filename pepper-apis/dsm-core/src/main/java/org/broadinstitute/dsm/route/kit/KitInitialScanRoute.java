package org.broadinstitute.dsm.route.kit;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.db.dao.kit.KitDaoImpl;
import org.broadinstitute.dsm.model.kit.KitInitialScanUseCase;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

public class KitInitialScanRoute extends KitStatusChangeRoute {

    public KitInitialScanRoute() {
        super(null);
    }

    @Override
    protected void processRequest() {
        KitInitialScanUseCase kitInitialScanUseCase = new KitInitialScanUseCase(kitPayload, new KitDaoImpl());
        scanErrorList.addAll(kitInitialScanUseCase.get());
    }

    @Override
    protected List<InitialScanPayload> getScanPayloads(String requestBody) {
        return ObjectMapperSingleton.readValue(requestBody, new TypeReference<List<InitialScanPayload>>() {
        });
    }
}
