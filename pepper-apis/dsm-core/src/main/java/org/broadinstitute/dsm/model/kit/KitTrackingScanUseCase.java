package org.broadinstitute.dsm.model.kit;

import java.util.Optional;

import org.apache.commons.lang3.NotImplementedException;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.route.kit.ScanPayload;

//connecting kit barcode to tracking id
public class KitTrackingScanUseCase extends BaseKitUseCase {

    public KitTrackingScanUseCase(KitPayload kitPayload, KitDao kitDao) {
        super(kitPayload, kitDao);
    }

    @Override
    protected Optional<ScanError> process(ScanPayload scanPayload) {
        String kitLabel = scanPayload.getKitLabel();
        String trackingReturnId = scanPayload.getTrackingReturnId();
        KitRequestShipping kitRequestShipping = buildKitRequestShippingForInserting(kitLabel, trackingReturnId);
        Optional<ScanError> maybeScanError = insertKitRequest(kitRequestShipping);
        return maybeScanError;
    }

    private Optional<ScanError> insertKitRequest(KitRequestShipping kitRequestShipping) {
        return kitDao.insertKitTracking(kitRequestShipping, String.valueOf(kitPayload.getUserId()));
    }

    private KitRequestShipping buildKitRequestShippingForInserting(String kitLabel, String trackingReturnId) {
        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        kitRequestShipping.setTrackingId(trackingReturnId);
        kitRequestShipping.setKitLabel(kitLabel);
        return kitRequestShipping;
    }

    @Override
    protected Optional<ScanError> processRGPFinalScan(ScanPayload scanPayload) {
        throw new NotImplementedException();
    }

}
