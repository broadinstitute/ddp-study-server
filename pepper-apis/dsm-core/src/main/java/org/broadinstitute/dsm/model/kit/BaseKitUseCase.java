package org.broadinstitute.dsm.model.kit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import lombok.AllArgsConstructor;
import lombok.Setter;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.route.kit.KitStatusChangeRoute;
import org.broadinstitute.dsm.route.kit.ScanPayload;

@AllArgsConstructor
@Setter
public abstract class BaseKitUseCase implements Supplier<List<KitStatusChangeRoute.ScanError>> {

    protected KitPayload kitPayload;
    protected KitDao kitDao;

    @Override
    public List<KitStatusChangeRoute.ScanError> get() {
        List<KitStatusChangeRoute.ScanError> result = new ArrayList<>();
        for (ScanPayload scanPayload : kitPayload.getScanPayloads()) {
            process(scanPayload).ifPresent(result::add);
        }
        return result;
    }

    protected abstract Optional<KitStatusChangeRoute.ScanError> process(ScanPayload scanPayload);

    protected boolean isKitUpdateSuccessful(Optional<KitStatusChangeRoute.ScanError> maybeScanError) {
        return maybeScanError.isEmpty();
    }

}
