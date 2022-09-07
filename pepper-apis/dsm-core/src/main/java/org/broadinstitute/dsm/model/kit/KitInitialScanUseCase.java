package org.broadinstitute.dsm.model.kit;

import java.util.Optional;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.route.kit.ScanPayload;
import org.broadinstitute.dsm.statics.UserErrorMessages;

public class KitInitialScanUseCase extends BaseKitUseCase {

    public KitInitialScanUseCase(KitPayload kitPayload, KitDao kitDao) {
        super(kitPayload, kitDao);
    }

    @Override
    protected Optional<ScanError> process(ScanPayload scanPayload) {
        String kitLabel = scanPayload.getKitLabel();
        String hruid = scanPayload.getHruid();
        Optional<ScanError> maybeScanError = updateKitRequest(hruid, kitLabel);
        // not writing into ES because same info will get written per final scan into ES
        return maybeScanError;
    }

    private Optional<ScanError> updateKitRequest(String hruid, String kit) {
        Optional<KitRequestShipping> maybeKitRequestShipping = kitDao.getKitByHruid(hruid);
        if (maybeKitRequestShipping.isPresent()) {
            KitRequestShipping kitRequestShipping = maybeKitRequestShipping.get();
            kitRequestShipping.setKitLabel(kit);
            kitRequestShipping.setHruid(hruid);
            return kitDao.updateKitLabel(kitRequestShipping);
        }
        return Optional.ofNullable(new ScanError(kit, "No kit for participant with ShortId \"" + hruid + "\" was not found.\n"
                + UserErrorMessages.IF_QUESTIONS_CONTACT_DEVELOPER));
    }
}
