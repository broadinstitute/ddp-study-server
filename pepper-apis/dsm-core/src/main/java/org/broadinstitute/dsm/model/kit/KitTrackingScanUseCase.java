package org.broadinstitute.dsm.model.kit;

import java.util.Optional;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.model.elastic.export.painless.PutToNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.route.kit.KitStatusChangeRoute;
import org.broadinstitute.dsm.route.kit.ScanPayload;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KitTrackingScanUseCase extends BaseKitUseCase {

    private static final Logger logger = LoggerFactory.getLogger(KitTrackingScanUseCase.class);

    public KitTrackingScanUseCase(KitPayload kitPayload, KitDao kitDao) {
        super(kitPayload, kitDao);
    }

    @Override
    protected Optional<KitStatusChangeRoute.ScanError> process(ScanPayload scanPayload) {
        String kitLabel = scanPayload.getKitLabel();
        String trackingReturnId = scanPayload.getTrackingReturnId();
        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        kitRequestShipping.setTrackingId(trackingReturnId);
        kitRequestShipping.setKitLabel(kitLabel);
        Optional<KitStatusChangeRoute.ScanError> maybeScanError =
                kitDao.insertKitTracking(kitRequestShipping, String.valueOf(kitPayload.getUserId()));
        if (isKitUpdateSuccessful(maybeScanError)) {
            try {
                UpsertPainlessFacade.of(DBConstants.DDP_KIT_REQUEST_ALIAS, kitRequestShipping, kitPayload.getDdpInstanceDto(),
                        "kitLabel", "kitLabel", trackingReturnId, new PutToNestedScriptBuilder()).export();
            } catch (Exception e) {
                logger.error(String.format("Error updating kit label for kit with label: %s", trackingReturnId));
                e.printStackTrace();
            }
        }
        return maybeScanError;
    }

}
