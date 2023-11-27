package org.broadinstitute.dsm.route.kit;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.db.dao.kit.KitDaoImpl;
import org.broadinstitute.dsm.model.kit.KitSentUseCase;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

public class SentKitRoute extends KitStatusChangeRoute {

    public SentKitRoute() {
        super(null);
    }

    @Override
    protected void processRequest() {
        KitSentUseCase kitSentUseCase = new KitSentUseCase(kitPayload, new KitDaoImpl());
        scanResultList.addAll(kitSentUseCase.get());
    }

    @Override
    protected List<SentAndFinalScanPayload> getScanPayloads(String requestBody) {
        return ObjectMapperSingleton.readValue(requestBody, new TypeReference<List<SentAndFinalScanPayload>>() {});
    }
}
