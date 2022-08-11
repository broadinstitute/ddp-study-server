package org.broadinstitute.dsm.model.kit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.broadinstitute.dsm.route.KitPayload;
import org.broadinstitute.dsm.route.KitStatusChangeRoute;
import org.broadinstitute.dsm.route.ScanPayload;

@AllArgsConstructor
@Setter
public abstract class BaseKitUseCase implements Supplier<List<KitStatusChangeRoute.ScanError>> {

    protected KitPayload kitPayload;

    @Override
    public List<KitStatusChangeRoute.ScanError> get() {
        List<KitStatusChangeRoute.ScanError> result = new ArrayList<>();
        for (ScanPayload scanPayload: kitPayload.getScanPayloads()) {
            result.add(process(scanPayload));
        }
        return result;
    }

    protected abstract KitStatusChangeRoute.ScanError process(ScanPayload scanPayload);

}
