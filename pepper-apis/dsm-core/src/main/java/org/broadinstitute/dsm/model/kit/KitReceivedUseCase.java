package org.broadinstitute.dsm.model.kit;

import java.util.Optional;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.model.at.ReceiveKitRequest;
import org.broadinstitute.dsm.route.KitPayload;
import org.broadinstitute.dsm.route.KitStatusChangeRoute;
import org.broadinstitute.dsm.route.ScanPayload;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KitReceivedUseCase extends BaseKitUseCase {

    private static final Logger logger = LoggerFactory.getLogger(KitReceivedUseCase.class);

    private final KitDao kitDao;
    private final NotificationUtil notificationUtil;

    public KitReceivedUseCase(KitPayload kitPayload, KitDao kitDao, NotificationUtil notificationUtil) {
        super(kitPayload);
        this.kitDao = kitDao;
        this.notificationUtil = notificationUtil;
    }

    @Override
    protected Optional<KitStatusChangeRoute.ScanError> process(ScanPayload scanPayload) {
        String kit = scanPayload.getKit();
        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        kitRequestShipping.setKitLabel(kit);
        Optional<KitStatusChangeRoute.ScanError> maybeScanError =
                kitDao.updateKitReceived(kitRequestShipping, String.valueOf(kitPayload.getUserId()));
        if (maybeScanError.isPresent()) {
            if (isReceiveATKitRequest(kit)) {
                maybeScanError = Optional.empty();
            } else {
                logger.warn("SM-ID kit_label " + kit + " does not exist or was already scanned as received");
            }
        } else {
            logger.info("Updated kitRequest w/ SM-ID kit_label " + kitRequestShipping.getKitLabel());
        }
        return maybeScanError;
    }

    private boolean isReceiveATKitRequest(String kit) {
        return ReceiveKitRequest.receiveATKitRequest(notificationUtil, kit);
    }
}
