package org.broadinstitute.dsm.model.kit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import lombok.Setter;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.route.kit.ScanPayload;
import spark.utils.StringUtils;

@Setter
public abstract class BaseKitUseCase implements Supplier<List<ScanError>> {

    protected KitPayload kitPayload;
    protected KitDao kitDao;

    private BaseKitUseCase decoratedScanUseCase;

    public BaseKitUseCase(KitPayload kitPayload, KitDao kitDao) {
        this.kitPayload = kitPayload;
        this.kitDao = kitDao;
    }

    @Override
    public List<ScanError> get() {
        List<ScanError> result = new ArrayList<>();
        for (ScanPayload scanPayload : kitPayload.getScanPayloads()) {
            process(scanPayload).ifPresentOrElse(maybeScanError -> result.add(maybeScanError), () -> result.add(null));
        }
        return result;
    }

    public List<ScanError> getRGPFinalScan() {
        List<ScanError> result = new ArrayList<>();
        for (ScanPayload scanPayload : kitPayload.getScanPayloads()) {
            processRGPFinalScan(scanPayload).ifPresent(result::add);
        }
        return result;
    }

    protected abstract Optional<ScanError> process(ScanPayload scanPayload);
    protected abstract Optional<ScanError> processRGPFinalScan(ScanPayload scanPayload);

    protected boolean isKitUpdateSuccessful(Optional<ScanError> maybeScanError, String bspCollaboratorParticipantId) {
        return maybeScanError.isEmpty() || isScanErrorOnlyBspParticipantId(maybeScanError.get(), bspCollaboratorParticipantId);
    }

    private boolean isScanErrorOnlyBspParticipantId(ScanError scanError, String bspCollaboratorParticipantId) {
        return (StringUtils.isBlank(scanError.getError()) && StringUtils.isBlank(scanError.getKit()))
                || (scanError.getError().equals(scanError.getKit()) && scanError.getError().equals(bspCollaboratorParticipantId));
    }

    protected BaseKitUseCase getDecoratedScanUseCase() {
        if (Objects.isNull(decoratedScanUseCase)) {
            decoratedScanUseCase = new NullObjectDecorator();
        }
        return decoratedScanUseCase;
    }
}
