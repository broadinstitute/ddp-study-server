package org.broadinstitute.dsm.model.kit;

import java.util.Optional;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.route.kit.ScanPayload;

public class KitInitialScanUseCase extends BaseKitUseCase {

    public KitInitialScanUseCase(KitPayload kitPayload, KitDao kitDao) {
        super(kitPayload, kitDao);
    }

    @Override
    protected Optional<ScanError> process(ScanPayload scanPayload) {
        String kitLabel = scanPayload.getKitLabel();
        String hruid = scanPayload.getHruid();
        Optional<ScanError> maybeScanError = updateKitRequest(hruid, kitLabel);
        // not writing into ES because same info will get written per final scan into ES
        return maybeScanError;
    }

    private Optional<ScanError> updateKitRequest(String addValue, String kit) {
        KitRequestShipping kitRequestShipping = getKitRequestShipping(addValue, kit);
        return kitDao.updateKitByHruid(kitRequestShipping);
    }

    protected KitRequestShipping getKitRequestShipping(String addValue, String kit) {
        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        kitRequestShipping.setKitLabel(kit);
        kitRequestShipping.setShortId(addValue);
        return kitRequestShipping;
    }
}
