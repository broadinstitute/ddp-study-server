package org.broadinstitute.dsm.model.kit;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.model.Study;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.route.kit.ScanPayload;

public class PecgsDecorator extends BaseKitUseCase {


    public PecgsDecorator(KitPayload kitPayload, KitDao kitDao) {
        super(kitPayload, kitDao);
    }

    boolean hasPecgsPrefix(String kitLabel) {
        return StringUtils.isNotBlank(kitLabel) && kitLabel.startsWith("PECGS");
    }

    @Override
    protected Optional<ScanError> process(ScanPayload scanPayload) {
        if (kitDao.isBloodKit(scanPayload.getDdpLabel())
                && Study.isPECGS(kitPayload.getDdpInstanceDto().getInstanceName())
                && !hasPecgsPrefix(scanPayload.getKitLabel())) {
            return Optional.of(new ScanError(scanPayload.getDdpLabel(), "No \"PECGS\" prefix found. "
                    + "PE-CGS project blood kits should have a \"PECGS\" prefix on barcode. "
                    + "Please check to see if this is PE-CGS blood kit before proceeding."));
        }
        return Optional.empty();
    }
}
