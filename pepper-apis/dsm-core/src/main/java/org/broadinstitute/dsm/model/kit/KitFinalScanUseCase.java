package org.broadinstitute.dsm.model.kit;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.kitrequest.KitRequestDao;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.db.dto.ddp.kitrequest.KitRequestDto;
import org.broadinstitute.dsm.route.KitPayload;
import org.broadinstitute.dsm.route.KitStatusChangeRoute;
import org.broadinstitute.dsm.route.ScanPayload;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchDataUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

public class KitFinalScanUseCase extends KitFinalSentBaseUseCase {

    public KitFinalScanUseCase(KitPayload kitPayload, KitDao kitDao) {
        super(kitPayload, kitDao);
    }

    @Override
    protected Optional<KitStatusChangeRoute.ScanError> process(ScanPayload scanPayload) {
        Optional<KitStatusChangeRoute.ScanError> result;
        String addValue = scanPayload.getAddValue();
        String kit = scanPayload.getKit();
        if (kitDao.isBloodKit(kit)) {
            if (kitDao.hasTrackingScan(addValue)) {
                result = updateKitRequest(addValue, kit);
                trigerEventsIfSuccessfulKitUpdate(result, kit, getKitRequestShipping(addValue, kit));
                KitRequestDao kitRequestDao = new KitRequestDao();
                kitRequestDao.getKitRequestByLabel(kit).ifPresent(this::writeSampleSentToES);
            } else {
                result = Optional.of(
                        new KitStatusChangeRoute.ScanError(kit, "Kit with DSM Label \"" + kit + "\" does not have a Tracking Label"));
            }
        } else {
            result = updateKitRequest(addValue, kit);
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
