package org.broadinstitute.dsm.model.kit;

import java.util.List;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.route.kit.ScanPayload;
import org.broadinstitute.dsm.statics.UserErrorMessages;

public class KitInitialScanUseCase extends BaseKitUseCase {

    public KitInitialScanUseCase(KitPayload kitPayload, KitDao kitDao) {
        super(kitPayload, kitDao);
    }

    @VisibleForTesting
    @Override
    protected Optional<ScanResult> process(ScanPayload scanPayload) {
        String kitLabel = scanPayload.getKitLabel();
        String hruid = scanPayload.getHruid();
        // not writing into ES because same info will get written per final scan into ES
        return updateKitRequest(hruid, kitLabel);
    }

    private Optional<ScanResult> updateKitRequest(String hruid, String kit) {
        List<KitRequestShipping> kitList = kitDao.getKitsByHruid(hruid);
        if (kitList != null && !kitList.isEmpty()) {
            if (kitList.size() > 2) {
                return Optional.ofNullable(new ScanResult(kit,
                        "Too many active kits found for \"" + hruid + "\".\n" + UserErrorMessages.IF_QUESTIONS_CONTACT_DEVELOPER));
            }
            Optional<KitRequestShipping> kitRequestWithPrefix =
                    kitList.stream().filter(k -> StringUtils.isNotBlank(k.getKitLabelPrefix())).findFirst();
            for (KitRequestShipping kitRequest : kitList) {
                if (isBloodKit(kitRequest, kit) || salivaAndBloodKitInQueue(kitRequest, kit, kitRequestWithPrefix)
                        || salivaAndNoBloodInQueue(kitRequest, kitRequestWithPrefix)) {
                    setKitInformation(kitRequest, kit, hruid);
                    return kitDao.updateKitLabel(kitRequest);
                }
            }
        }
        return Optional.ofNullable(new ScanResult(kit, "Kit for participant with ShortId \"" + hruid + "\" was not found.\n"
                + UserErrorMessages.IF_QUESTIONS_CONTACT_DEVELOPER));
    }

    private boolean salivaAndNoBloodInQueue(KitRequestShipping kitRequest, Optional<KitRequestShipping> kitRequestWithPrefix) {
        return StringUtils.isBlank(kitRequest.getKitLabelPrefix()) && !kitRequestWithPrefix.isPresent();
    }

    private boolean salivaAndBloodKitInQueue(KitRequestShipping kitRequest, String kitLabel,
                                             Optional<KitRequestShipping> kitRequestWithPrefix) {
        return StringUtils.isBlank(kitRequest.getKitLabelPrefix())
                && kitRequestWithPrefix.isPresent() && !kitLabel.startsWith(kitRequestWithPrefix.get().getKitLabelPrefix());
    }

    private boolean isBloodKit(KitRequestShipping kitRequest, String kitLabel) {
        return StringUtils.isNotBlank(kitRequest.getKitLabelPrefix()) && kitLabel.startsWith(kitRequest.getKitLabelPrefix());
    }

    private void setKitInformation(KitRequestShipping kitRequestShipping, String kit, String hruid) {
        kitRequestShipping.setKitLabel(kit);
        kitRequestShipping.setHruid(hruid);
    }

    @Override
    protected Optional<ScanResult> processRGPFinalScan(ScanPayload scanPayload) {
        throw new NotImplementedException();
    }
}
