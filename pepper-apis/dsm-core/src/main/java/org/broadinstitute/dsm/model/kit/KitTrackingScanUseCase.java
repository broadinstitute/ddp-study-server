package org.broadinstitute.dsm.model.kit;

import java.util.Optional;

import org.apache.commons.lang3.NotImplementedException;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.route.kit.ScanPayload;

/**
 * Creates a row in ddp_kit_tracking, returning
 * a scan error if something goes wrong in the process.
 */
public class KitTrackingScanUseCase extends BaseKitUseCase {

    public KitTrackingScanUseCase(KitPayload kitPayload, KitDao kitDao) {
        super(kitPayload, kitDao);
    }

    @Override
    protected Optional<ScanError> process(ScanPayload scanPayload) {
        return kitDao.insertKitTrackingIfNotExists(scanPayload.getKitLabel(), scanPayload.getTrackingReturnId(),
                kitPayload.getUserId());
    }

    @Override
    protected Optional<ScanError> processRGPFinalScan(ScanPayload scanPayload) {
        throw new NotImplementedException();
    }

}
