package org.broadinstitute.dsm.model.kit;

import java.util.Optional;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.route.kit.KitStatusChangeRoute;
import org.broadinstitute.dsm.route.kit.ScanPayload;

public class KitSentUseCase extends KitFinalSentBaseUseCase {

    public KitSentUseCase(KitPayload kitPayload, KitDao kitDao) {
        super(kitPayload, kitDao);
    }

    @Override
    protected Optional<KitStatusChangeRoute.ScanError> process(ScanPayload scanPayload) {
        String kitLabel = scanPayload.getKitLabel();
        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        kitRequestShipping.setKitLabel(kitLabel);
        Optional<KitStatusChangeRoute.ScanError> result =
                kitDao.updateKitRequest(kitRequestShipping, String.valueOf(kitPayload.getUserId()));
        trigerEventsIfSuccessfulKitUpdate(result, kitLabel, kitRequestShipping);
        return result;
    }
}
