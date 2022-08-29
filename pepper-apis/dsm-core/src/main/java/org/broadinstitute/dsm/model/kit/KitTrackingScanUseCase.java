package org.broadinstitute.dsm.model.kit;

import java.util.Optional;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.model.elastic.export.painless.PutToNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.route.kit.ScanPayload;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//connecting kit barcode to tracking id
public class KitTrackingScanUseCase extends BaseKitUseCase {

    private static final Logger logger = LoggerFactory.getLogger(KitTrackingScanUseCase.class);

    public KitTrackingScanUseCase(KitPayload kitPayload, KitDao kitDao) {
        super(kitPayload, kitDao);
    }

    @Override
    protected Optional<ScanError> process(ScanPayload scanPayload) {
        String kitLabel = scanPayload.getKitLabel();
        String trackingReturnId = scanPayload.getTrackingReturnId();
        KitRequestShipping kitRequestShipping = buildKitRequestShippingForInserting(kitLabel, trackingReturnId);
        Optional<ScanError> maybeScanError =
                insertKitRequest(kitRequestShipping);
        if (isKitUpdateSuccessful(maybeScanError)) {
            exportToElasticSearch(trackingReturnId, kitRequestShipping);
        }
        return maybeScanError;
    }

    private void exportToElasticSearch(String trackingReturnId, KitRequestShipping kitRequestShipping) {
        try {
            UpsertPainlessFacade.of(DBConstants.DDP_KIT_REQUEST_ALIAS, kitRequestShipping, kitPayload.getDdpInstanceDto(),
                    "kitLabel", "kitLabel", trackingReturnId, new PutToNestedScriptBuilder()).export();
        } catch (Exception e) {
            logger.error(String.format("Error updating kit label for kit with label: %s", trackingReturnId));
            e.printStackTrace();
        }
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

}
