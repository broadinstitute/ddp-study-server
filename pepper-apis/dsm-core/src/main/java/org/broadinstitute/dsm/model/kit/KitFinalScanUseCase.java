package org.broadinstitute.dsm.model.kit;

import java.util.Optional;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.kitrequest.KitRequestDao;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.route.KitPayload;
import org.broadinstitute.dsm.route.KitStatusChangeRoute;
import org.broadinstitute.dsm.route.ScanPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KitFinalScanUseCase extends BaseKitUseCase {
    private KitDao kitDao;

    public KitFinalScanUseCase(KitPayload kitPayload, KitDao kitDao) {
        super(kitPayload);
        this.kitDao = kitDao;
    }

    @Override
    protected Optional<KitStatusChangeRoute.ScanError> process(ScanPayload scanPayload) {
        Optional<KitStatusChangeRoute.ScanError> result = Optional.empty();
        String addValue = scanPayload.getAddValue();
        String kit = scanPayload.getKit();
        if (kitDao.isBloodKit(kit)) {
            if (kitDao.hasTrackingScan(addValue)) {
                Optional<KitStatusChangeRoute.ScanError> maybeScanError = updateKitRequest(addValue, kit);
                if (isKitUpdateSuccessful(maybeScanError)) {
                    triggerEvents(kit, getKitRequestShipping(addValue, kit));
                } else {
                    result = maybeScanError;
                }
                KitRequestDao kitRequestDao = new KitRequestDao();
                kitRequestDao.getKitRequestByLabel(kit).ifPresent(KitStatusChangeRoute::writeSampleSentToES);
            } else {
                result = Optional.of(
                        new KitStatusChangeRoute.ScanError(kit, "Kit with DSM Label \"" + kit + "\" does not have a Tracking Label"));
            }
        } else {
            result = updateKitRequest(addValue, kit);
        }
        return result;
    }

    private Optional<KitStatusChangeRoute.ScanError> updateKitRequest(String addValue, String kit) {
        KitRequestShipping kitRequestShipping = getKitRequestShipping(addValue, kit);
        return kitDao.updateKitRequest(kitRequestShipping, String.valueOf(kitPayload.getUserId()));
    }

    private KitRequestShipping getKitRequestShipping(String addValue, String kit) {
        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        kitRequestShipping.setKitLabel(addValue);
        kitRequestShipping.setDdpLabel(kit);
        return kitRequestShipping;
    }

}
