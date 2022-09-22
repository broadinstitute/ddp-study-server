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
    }

    @Override
    protected Optional<ScanError> process(ScanPayload scanPayload) {
        Optional<ScanError> result;
        String kitLabel = scanPayload.getKitLabel();
        String ddpLabel = scanPayload.getDdpLabel();

        Optional<KitRequestShipping> kitByDdpLabel = kitDao.getKitByDdpLabel(ddpLabel, kitLabel);
        if (kitByDdpLabel.isPresent()) {
            KitRequestShipping kitRequestShipping = kitByDdpLabel.get();
            if (StringUtils.isNotBlank(kitRequestShipping.getKitLabelPrefix()) && !kitLabel.startsWith(
                    kitRequestShipping.getKitLabelPrefix())) {
                //prefix is configured and doesn't match kit label
                result = Optional.of((new ScanError(ddpLabel, "No " + kitRequestShipping.getKitLabelPrefix() + " prefix found. "
                        + "Please check to see if this is the correct kit for this project before proceeding.")));
            } else if (kitRequestShipping.getKitLabelLength() != null && kitRequestShipping.getKitLabelLength() != 0
                    && kitLabel.length() != kitRequestShipping.getKitLabelLength()) {
                //barcode length doesn't fit configured length
                result = Optional.of(new ScanError(ddpLabel,
                        "Barcode doesn't contain " + kitRequestShipping.getKitLabelLength() + " digits. You can manually enter any"
                                + " missing digits above."));
            } else if ((kitRequestShipping.isKitRequiringTrackingScan() && kitRequestShipping.hasTrackingScan()) || (
                    !kitRequestShipping.isKitRequiringTrackingScan())) {
                //tracking scan needed and done OR no tracking scan needed
                if (StringUtils.isNotEmpty(kitRequestShipping.getKitLabel()) && kitLabel.equals(kitRequestShipping.getKitLabel())
                        || StringUtils.isEmpty(kitRequestShipping.getKitLabel())) {
                    result = updateKitRequest(kitLabel, ddpLabel, getBspCollaboratorParticipantId(kitRequestShipping));
                    trigerEventsIfSuccessfulKitUpdate(result, ddpLabel, getKitRequestShipping(kitLabel, ddpLabel));
                    this.writeSampleSentToES(kitRequestShipping);
                } else {
                    result = Optional.of(
                            new ScanError(ddpLabel, "Kit Label " + kitLabel + " was scanned on Initial Scan page with another ShortID"));
                }
            } else if (kitRequestShipping.isKitRequiringTrackingScan() && !kitRequestShipping.hasTrackingScan()) {
                //tracking scan required and missing
                result = Optional.of(new ScanError(ddpLabel, "Kit with DSM Label " + ddpLabel + " does not have a Tracking Label"));
            } else {
                //wasn't saved
                result = Optional.of(new ScanError(ddpLabel, "Kit with DSM Label " + ddpLabel + " was not saved successfully"));
            }
        } else {
            //DSM label doesn't exist
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

    private String getBspCollaboratorParticipantId(KitRequestShipping kitRequestShipping) {
        return StringUtils.isEmpty(kitRequestShipping.getKitLabel()) ? null : kitRequestShipping.getBspCollaboratorParticipantId();
    }

}
