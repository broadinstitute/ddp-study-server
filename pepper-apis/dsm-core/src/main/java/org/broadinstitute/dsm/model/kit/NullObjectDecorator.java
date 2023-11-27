package org.broadinstitute.dsm.model.kit;

import java.util.Optional;

import org.apache.commons.lang3.NotImplementedException;
import org.broadinstitute.dsm.route.kit.ScanPayload;

public class NullObjectDecorator extends BaseKitUseCase {

    public NullObjectDecorator() {
        super(null, null);
    }

    @Override
    protected Optional<ScanError> process(ScanPayload scanPayload) {
        return Optional.empty();
    }

    @Override
    protected Optional<ScanError> processRGPFinalScan(ScanPayload scanPayload) {
        throw new NotImplementedException();
    }
}
