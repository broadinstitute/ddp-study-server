package org.broadinstitute.dsm.route.kit;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.model.kit.KitFinalScanUseCase;


public class RGPKitFinalScanRoute  extends KitStatusChangeRoute {

    public RGPKitFinalScanRoute() {
        super(null);
    }

    @Override
    protected void processRequest() {
        KitFinalScanUseCase kitFinalScanUseCase = new KitFinalScanUseCase(kitPayload, new KitDao());
        scanResultList.addAll(kitFinalScanUseCase.getRGPFinalScan());
    }

    @Override
    protected List<RGPRnaKitFinalScanPayLoad> getScanPayloads(String requestBody) {
        return new Gson().fromJson(requestBody,  new TypeToken<List<RGPRnaKitFinalScanPayLoad>>(){}.getType());
    }
}
