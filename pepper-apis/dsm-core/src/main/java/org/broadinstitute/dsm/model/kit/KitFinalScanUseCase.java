package org.broadinstitute.dsm.model.kit;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.route.kit.ScanPayload;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchDataUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

//connects kit request with an actual mf barcode on the tub
public class KitFinalScanUseCase extends KitFinalSentBaseUseCase {

    public KitFinalScanUseCase(KitPayload kitPayload, KitDao kitDao) {
        super(kitPayload, kitDao);
        setDecoratedScanUseCase(new PecgsDecorator(kitPayload, kitDao));
    }

    @Override
    protected Optional<ScanError> process(ScanPayload scanPayload) {
        Optional<ScanError> result;
        String kitLabel = scanPayload.getKitLabel();
        String ddpLabel = scanPayload.getDdpLabel();
        if (isSalivaKit(ddpLabel) && kitLabel.length() < 14) {
            return Optional.of(
                    new ScanError(ddpLabel, "Barcode contains less than 14 digits, " + "You can manually enter any missing digits above."));
        }
        Optional<ScanError> decoratedProcess = getDecoratedScanUseCase().process(scanPayload);
        if (decoratedProcess.isPresent()) {
            return decoratedProcess;
        }
        Optional<KitRequestShipping> kitByDdpLabel = kitDao.getKitByDdpLabel(ddpLabel, kitLabel);
        if (kitByDdpLabel.isPresent()) {
            KitRequestShipping kitRequestShipping = kitByDdpLabel.get();
            if (kitRequestShipping.isBloodKit()) {
                if (kitRequestShipping.hasTrackingScan()) {
                    if (StringUtils.isNotEmpty(kitRequestShipping.getKitLabel()) && kitLabel.equals(kitRequestShipping.getKitLabel())
                            || StringUtils.isEmpty(kitRequestShipping.getKitLabel())) {
                        result = updateKitRequest(kitLabel, ddpLabel, kitRequestShipping.getBspCollaboratorParticipantId());
                        trigerEventsIfSuccessfulKitUpdate(result, ddpLabel, getKitRequestShipping(kitLabel, ddpLabel));
                        this.writeSampleSentToES(kitRequestShipping);
                    } else {
                        result = Optional.of(
                                new ScanError(ddpLabel, "Kit Label was scanned on Initial Scan page with another ShortID " + kitLabel));
                    }
                } else {
                    result = Optional.of(new ScanError(ddpLabel, "Kit with DSM Label " + ddpLabel + " does not have a Tracking Label"));
                }
            } else {
                if (StringUtils.isNotEmpty(kitRequestShipping.getKitLabel()) && kitLabel.equals(kitRequestShipping.getKitLabel())
                        || StringUtils.isEmpty(kitRequestShipping.getKitLabel())) {
                    result = updateKitRequest(kitLabel, ddpLabel, kitRequestShipping.getBspCollaboratorParticipantId());
                } else {
                    result = Optional.of(
                            new ScanError(ddpLabel, "Kit Label " + kitLabel + " was scanned on Initial Scan page with another ShortID"));
                }
            }
        } else {
            result = Optional.of(new ScanError(ddpLabel, "Kit with DSM Label " + ddpLabel + " does not exist"));
        }
        return result;
    }

    private Optional<ScanError> updateKitRequest(String addValue, String kit, String bspCollaboratorParticipantId) {
        KitRequestShipping kitRequestShipping = getKitRequestShipping(addValue, kit);
        kitRequestShipping.setBspCollaboratorParticipantId(bspCollaboratorParticipantId);
        return kitDao.updateKitRequest(kitRequestShipping, String.valueOf(kitPayload.getUserId()));
    }

    protected KitRequestShipping getKitRequestShipping(String addValue, String kit) {
        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        kitRequestShipping.setKitLabel(addValue);
        kitRequestShipping.setDdpLabel(kit);
        return kitRequestShipping;
    }

    private void writeSampleSentToES(KitRequestShipping kitRequest) {
        int ddpInstanceId = Long.valueOf(kitRequest.getDdpInstanceId()).intValue();
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceById(ddpInstanceId);
        Map<String, Object> nameValuesMap = new HashMap<>();
        ElasticSearchDataUtil.setCurrentStrictYearMonthDay(nameValuesMap, ESObjectConstants.SENT);
        if (ddpInstance != null && kitRequest.getDdpKitRequestId() != null && kitRequest.getDdpParticipantId() != null) {
            ElasticSearchUtil.writeSample(ddpInstance, kitRequest.getDdpKitRequestId(), kitRequest.getDdpParticipantId(),
                    ESObjectConstants.SAMPLES, ESObjectConstants.KIT_REQUEST_ID, nameValuesMap);
        }
    }

    private boolean isSalivaKit(String ddpLabel) {
        return !kitDao.isBloodKit(ddpLabel);
    }

}
