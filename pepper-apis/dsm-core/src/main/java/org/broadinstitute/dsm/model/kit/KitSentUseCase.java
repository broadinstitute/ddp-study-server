package org.broadinstitute.dsm.model.kit;

import java.util.Optional;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.route.KitPayload;
import org.broadinstitute.dsm.route.KitStatusChangeRoute;
import org.broadinstitute.dsm.route.ScanPayload;

public class KitSentUseCase extends KitFinalSentBaseUseCase {

    public KitSentUseCase(KitPayload kitPayload, KitDao kitDao) {
        super(kitPayload, kitDao);
    }

    @Override
    protected Optional<KitStatusChangeRoute.ScanError> process(ScanPayload scanPayload) {
        String kit = scanPayload.getKit();
        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        kitRequestShipping.setKitLabel(kit);
        Optional<KitStatusChangeRoute.ScanError> result =
                kitDao.updateKitRequest(kitRequestShipping, String.valueOf(kitPayload.getUserId()));
        trigerEventsIfSuccessfulKitUpdate(result, kit, kitRequestShipping);
        return result;
    }
}
