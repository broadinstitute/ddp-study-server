package org.broadinstitute.dsm.route.kit;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.model.kit.KitTrackingScanUseCase;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

public class KitTrackingScanRoute extends KitStatusChangeRoute {
    public KitTrackingScanRoute() {
        super(null);
    }

    @Override
    protected void processRequest() {
        KitTrackingScanUseCase kitTrackingScanUseCase = new KitTrackingScanUseCase(kitPayload, new KitDao());
        scanResultList.addAll(kitTrackingScanUseCase.get());
    }

    @Override
    protected List<TrackingScanPayload> getScanPayloads(String requestBody) {
        return ObjectMapperSingleton.readValue(requestBody, new TypeReference<List<TrackingScanPayload>>() {
        });
    }
}
