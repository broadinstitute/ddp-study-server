package org.broadinstitute.dsm.model.kit;

import java.util.Optional;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.kitrequest.KitRequestDao;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.model.KitDDPNotification;
import org.broadinstitute.dsm.model.elastic.export.painless.PutToNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.route.KitPayload;
import org.broadinstitute.dsm.route.KitStatusChangeRoute;
import org.broadinstitute.dsm.route.ScanPayload;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.DSMConfig;
import org.broadinstitute.dsm.util.EventUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KitFinalScanUseCase extends BaseKitUseCase {

    private static final Logger logger = LoggerFactory.getLogger(KitFinalScanUseCase.class);

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

    private boolean isKitUpdateSuccessful(Optional<KitStatusChangeRoute.ScanError> maybeScanError) {
        return maybeScanError.isEmpty();
    }

    private void triggerEvents(String kit, KitRequestShipping kitRequestShipping) {
        logger.info("Updated kitRequests w/ ddp_label " + kitRequestShipping.getDdpLabel());
        KitDDPNotification kitDDPNotification = KitDDPNotification.getKitDDPNotification(
                DSMConfig.getSqlFromConfig(ApplicationConfigConstants.GET_SENT_KIT_INFORMATION_FOR_NOTIFICATION_EMAIL), kit,
                1);
        if (kitDDPNotification != null) {
            EventUtil.triggerDDP(conn, kitDDPNotification);
        }
        try {
            UpsertPainlessFacade.of(DBConstants.DDP_KIT_REQUEST_ALIAS, kitRequestShipping,
                    kitPayload.getDdpInstanceDto(), "ddpLabel", "ddpLabel",
                    kit, new PutToNestedScriptBuilder()).export();
        } catch (Exception e) {
            logger.error(String.format("Error updating ddp label for kit with label: %s", kit));
            e.printStackTrace();
        }
    }

}
