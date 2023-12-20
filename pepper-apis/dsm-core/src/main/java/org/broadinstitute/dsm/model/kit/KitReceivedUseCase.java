package org.broadinstitute.dsm.model.kit;

import java.util.Optional;

import org.apache.commons.lang3.NotImplementedException;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.model.at.ReceiveKitRequest;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.route.kit.ScanPayload;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//used before we had the connection with GP, so we needed a way to tell dsm that a kit was back
public class KitReceivedUseCase extends BaseKitUseCase {

    private static final Logger logger = LoggerFactory.getLogger(KitReceivedUseCase.class);

    private final NotificationUtil notificationUtil;

    public KitReceivedUseCase(KitPayload kitPayload, KitDao kitDao, NotificationUtil notificationUtil) {
        super(kitPayload, kitDao);
        this.notificationUtil = notificationUtil;
    }

    @Override
    protected Optional<ScanResult> process(ScanPayload scanPayload) {
        String kitLabel = scanPayload.getKitLabel();
        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        kitRequestShipping.setKitLabel(kitLabel);
        Optional<ScanResult> maybeScanError =
                kitDao.updateKitReceived(kitRequestShipping, String.valueOf(kitPayload.getUserId()));
        if (!isKitUpdateSuccessful(maybeScanError, kitRequestShipping.getBspCollaboratorParticipantId())) {
            if (isReceiveATKitRequest(kitLabel)) {
                maybeScanError = Optional.empty();
            } else {
                logger.warn("SM-ID kit_label " + kitLabel + " does not exist or was already scanned as received");
            }
        } else {
            logger.info("Updated kitRequest w/ SM-ID kit_label " + kitRequestShipping.getKitLabel());
        }
        return maybeScanError;
    }

    private boolean isReceiveATKitRequest(String kit) {
        return ReceiveKitRequest.receiveATKitRequest(notificationUtil, kit);
    }

    @Override
    protected Optional<ScanResult> processRGPFinalScan(ScanPayload scanPayload) {
        throw new NotImplementedException();
    }

}
