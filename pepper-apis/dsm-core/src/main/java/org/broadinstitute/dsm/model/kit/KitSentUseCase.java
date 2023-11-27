package org.broadinstitute.dsm.model.kit;

import java.util.Optional;

import org.apache.commons.lang3.NotImplementedException;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.route.kit.ScanPayload;

//setting a kit to sent without the final scan (where there is no barcode)
public class KitSentUseCase extends KitFinalSentBaseUseCase {

    public KitSentUseCase(KitPayload kitPayload, KitDao kitDao) {
        super(kitPayload, kitDao);
    }

    @Override
    protected Optional<ScanResult> process(ScanPayload scanPayload) {
        String kitLabel = scanPayload.getKitLabel();
        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        kitRequestShipping.setKitLabel(kitLabel);
        Optional<ScanResult> result =
                kitDao.updateKitScanInfo(kitRequestShipping, String.valueOf(kitPayload.getUserId()));
        trigerEventsIfSuccessfulKitUpdate(result, kitLabel, kitRequestShipping);
        return result;
    }

    @Override
    protected Optional<ScanResult> processRGPFinalScan(ScanPayload scanPayload) {
        throw new NotImplementedException();
    }
}
