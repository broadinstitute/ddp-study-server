package org.broadinstitute.dsm.model.kit;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.kitrequest.KitRequestDao;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.db.dto.ddp.kitrequest.KitRequestDto;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.route.kit.KitStatusChangeRoute;
import org.broadinstitute.dsm.route.kit.ScanPayload;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchDataUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

//connects kit request with an actual mf barcode on the tub
public class KitFinalScanUseCase extends KitFinalSentBaseUseCase {

    public KitFinalScanUseCase(KitPayload kitPayload, KitDao kitDao) {
        super(kitPayload, kitDao);
    }

    @Override
    protected Optional<KitStatusChangeRoute.ScanError> process(ScanPayload scanPayload) {
        Optional<KitStatusChangeRoute.ScanError> result;
        String kitLabel = scanPayload.getKitLabel();
        String ddpLabel = scanPayload.getDdpLabel();
        if (kitDao.isBloodKit(ddpLabel)) {
            if (kitDao.hasTrackingScan(kitLabel)) {
                result = updateKitRequest(kitLabel, ddpLabel);
                trigerEventsIfSuccessfulKitUpdate(result, ddpLabel, getKitRequestShipping(kitLabel, ddpLabel));
                KitRequestDao kitRequestDao = new KitRequestDao();
                kitRequestDao.getKitRequestByLabel(ddpLabel).ifPresent(this::writeSampleSentToES);
            } else {
                result = Optional.of(
                        new KitStatusChangeRoute.ScanError(
                                ddpLabel, "Kit with DSM Label " + ddpLabel + " does not have a Tracking Label"));
            }
        } else {
            result = updateKitRequest(kitLabel, ddpLabel);
        }
        return result;
    }

    private Optional<KitStatusChangeRoute.ScanError> updateKitRequest(String addValue, String kit) {
        KitRequestShipping kitRequestShipping = getKitRequestShipping(addValue, kit);
        return kitDao.updateKitRequest(kitRequestShipping, String.valueOf(kitPayload.getUserId()));
    }

    protected KitRequestShipping getKitRequestShipping(String addValue, String kit) {
        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        kitRequestShipping.setKitLabel(addValue);
        kitRequestShipping.setDdpLabel(kit);
        return kitRequestShipping;
    }

    private void writeSampleSentToES(KitRequestDto kitRequest) {
        int ddpInstanceId = kitRequest.getDdpInstanceId();
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceById(ddpInstanceId);
        Map<String, Object> nameValuesMap = new HashMap<>();
        ElasticSearchDataUtil.setCurrentStrictYearMonthDay(nameValuesMap, ESObjectConstants.SENT);
        if (ddpInstance != null && kitRequest.getDdpKitRequestId() != null && kitRequest.getDdpParticipantId() != null) {
            ElasticSearchUtil.writeSample(ddpInstance, kitRequest.getDdpKitRequestId(), kitRequest.getDdpParticipantId(),
                    ESObjectConstants.SAMPLES, ESObjectConstants.KIT_REQUEST_ID, nameValuesMap);
        }
    }

}
