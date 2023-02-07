package org.broadinstitute.dsm.model.kit;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.elastic.export.painless.PutToNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.route.kit.ScanPayload;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.KitUtil;

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
            if (StringUtils.isNotBlank(kitRequestShipping.getKitLabelPrefix())
                    && !kitLabel.startsWith(kitRequestShipping.getKitLabelPrefix())
                    && StringUtils.isBlank(kitRequestShipping.getMessage())) {
                //prefix is configured and doesn't match kit label and kit error message was not PECGS_RESEARCH
                result = Optional.of((new ScanError(ddpLabel, "No " + kitRequestShipping.getKitLabelPrefix() + " prefix found. "
                        + "Please check to see if this is the correct kit for this project before proceeding.")));
            } else if (StringUtils.isNotBlank(kitRequestShipping.getKitLabelPrefix())
                    && kitLabel.startsWith(kitRequestShipping.getKitLabelPrefix())
                    && StringUtils.isNotBlank(kitRequestShipping.getMessage())
                    && KitUtil.PECGS_RESEARCH.equals(kitRequestShipping.getMessage())) {
                //prefix is configured and match kit label and kit error message was PECGS_RESEARCH
                result = Optional.of((new ScanError(ddpLabel,
                        "Please check to see if this is the correct kit for this participant before proceeding.")));
            } else if (kitRequestShipping.getKitLabelLength() != null && kitRequestShipping.getKitLabelLength() != 0
                    && kitLabel.length() != kitRequestShipping.getKitLabelLength()) {
                //barcode length doesn't fit configured length
                result = Optional.of(new ScanError(ddpLabel,
                        "Barcode doesn't contain " + kitRequestShipping.getKitLabelLength() + " digits. You can manually enter any"
                                + " missing digits above."));
            } else if ((kitRequestShipping.isKitRequiringTrackingScan() && kitRequestShipping.hasTrackingScan())
                    || (!kitRequestShipping.isKitRequiringTrackingScan())) {
                //tracking scan needed and done OR no tracking scan needed
                //successfully scanned and going to update db and ES
                if (StringUtils.isNotEmpty(kitRequestShipping.getKitLabel()) && kitLabel.equals(kitRequestShipping.getKitLabel())
                        || StringUtils.isEmpty(kitRequestShipping.getKitLabel())) {
                    kitRequestShipping.setKitLabel(kitLabel);
                    kitRequestShipping.setDdpLabel(ddpLabel);
                    kitRequestShipping.setScanDate(System.currentTimeMillis());
                    result = updateKitRequest(kitRequestShipping);
                    trigerEventsIfSuccessfulKitUpdate(result, ddpLabel, kitRequestShipping);
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

    private Optional<ScanError> updateKitRequest(KitRequestShipping kitRequestShipping) {
        return kitDao.updateKitRequest(kitRequestShipping, String.valueOf(kitPayload.getUserId()));
    }

    private void writeSampleSentToES(KitRequestShipping kitRequest) {
        //need to add sent date to it TODO
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceId(kitRequest.getDdpInstanceId().intValue())
                .orElseThrow();
        try {
            UpsertPainlessFacade.of(DBConstants.DDP_KIT_REQUEST_ALIAS, kitRequest, ddpInstanceDto, ESObjectConstants.DSM_KIT_ID,
                    ESObjectConstants.DOC_ID,
                    Exportable.getParticipantGuid(kitRequest.getDdpParticipantId(), ddpInstanceDto.getEsParticipantIndex()),
                    new PutToNestedScriptBuilder()).export();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
