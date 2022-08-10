package org.broadinstitute.dsm.model.kit;

import java.util.List;
import java.util.function.Supplier;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.broadinstitute.dsm.route.KitPayload;
import org.broadinstitute.dsm.route.KitStatusChangeRoute;

@NoArgsConstructor
@AllArgsConstructor
@Setter
public abstract class BaseKitUseCase implements Supplier<List<KitStatusChangeRoute.ScanError>> {

    private KitPayload kitPayload;

    @Override
    public List<KitStatusChangeRoute.ScanError> get() {
        return null;
    }

    protected abstract void process();

}
