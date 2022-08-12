package org.broadinstitute.dsm.model.kit;

import java.util.Optional;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.model.elastic.export.painless.PutToNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.route.KitPayload;
import org.broadinstitute.dsm.route.KitStatusChangeRoute;
import org.broadinstitute.dsm.route.ScanPayload;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KitSentUseCase extends BaseKitUseCase {

    private static final Logger logger = LoggerFactory.getLogger(KitSentUseCase.class);

    private final KitDao kitDao;

    public KitSentUseCase(KitPayload kitPayload, KitDao kitDao) {
        super(kitPayload);
        this.kitDao = kitDao;
    }

    @Override
    protected Optional<KitStatusChangeRoute.ScanError> process(ScanPayload scanPayload) {
        String kit = scanPayload.getKit();
        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        kitRequestShipping.setKitLabel(kit);
        Optional<KitStatusChangeRoute.ScanError> maybeScanError =
                kitDao.updateKitRequest(kitRequestShipping, String.valueOf(kitPayload.getUserId()));
        if (isKitUpdateSuccessful(maybeScanError)) {
            triggerEvents(kit, kitRequestShipping);
        } else {
            result = maybeScanError;
        }
        return maybeScanError;
    }
}
