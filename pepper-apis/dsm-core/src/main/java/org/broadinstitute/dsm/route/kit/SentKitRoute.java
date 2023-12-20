package org.broadinstitute.dsm.route.kit;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.model.kit.KitSentUseCase;
import org.broadinstitute.dsm.model.kit.ScanResult;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

public class SentKitRoute extends KitStatusChangeRoute {

    public SentKitRoute() {
        super(null);
    }

    @Override
    protected List<ScanResult> processRequest(KitPayload kitPayload) {
        KitSentUseCase kitSentUseCase = new KitSentUseCase(kitPayload, new KitDao());
        return kitSentUseCase.get();
    }

    @Override
    protected List<SentAndFinalScanPayload> getScanPayloads(String requestBody) {
        return ObjectMapperSingleton.readValue(requestBody, new TypeReference<List<SentAndFinalScanPayload>>() {});
    }
}
