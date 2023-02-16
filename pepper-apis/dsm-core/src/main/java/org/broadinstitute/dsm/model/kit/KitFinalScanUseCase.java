package org.broadinstitute.dsm.model.kit;

import java.util.List;
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


    @Override
    public Optional<ScanError> processRGPFinalScan(ScanPayload scanPayload) {
        Optional<ScanError> result;
        String kitLabel = scanPayload.getKitLabel();
        String ddpLabel = scanPayload.getDdpLabel();
        String RNA = scanPayload.getRNA();

        Optional<List<KitRequestShipping>> kitsByDdpLabel = kitDao.getSubkitsByDdpLabel(ddpLabel, kitLabel);
        if (kitsByDdpLabel.isPresent()) {
            List<KitRequestShipping> subkits = kitsByDdpLabel.get();
            if (subkits == null || subkits.size() == 0){
                result = Optional.of(new ScanError(ddpLabel, "Kits with DDP Label " + ddpLabel + " does not exist"));
                return result;
            }
            if (subkits.size() > 2) {
                result = Optional.of(new ScanError(ddpLabel, "More than one kit with DDP Label " + ddpLabel + "was found"));
                return result;
            }

            KitRequestShipping subkit1 = subkits.stream().filter(subkit -> subkit.getKitTypeName().contains("BLOOD")).findFirst().orElseThrow();
            result = checkForKitErrors(subkit1, kitLabel, ddpLabel);
            if (!result.isEmpty()) {
                return result;
            }
             if (((subkit1.isKitRequiringTrackingScan() && subkit1.hasTrackingScan())
                    || (!subkit1.isKitRequiringTrackingScan()))) {
                //tracking scan needed and done OR no tracking scan needed
                //successfully scanned and going to update db and ES
                if (StringUtils.isNotEmpty(subkit1.getKitLabel()) && kitLabel.equals(subkit1.getKitLabel())
                        || StringUtils.isEmpty(subkit1.getKitLabel())) {
                    subkit1.setKitLabel(kitLabel);
                    subkit1.setDdpLabel(ddpLabel);
                    subkit1.setScanDate(System.currentTimeMillis());
                    result = updateKitRequest(subkit1);
                    trigerEventsIfSuccessfulKitUpdate(result, ddpLabel, subkit1);
                    this.writeSampleSentToES(subkit1);
                } else {
                    result = Optional.of(
                            new ScanError(ddpLabel, "Kit Label " + kitLabel + " was scanned on Initial Scan page with another ShortID"));
                    return result;
                }
            } else {
                 result = Optional.of(
                         new ScanError(ddpLabel, "DDP Label " + subkit1.getDdpLabel() + " requires tracking scan"));
                 return result;
             }
            KitRequestShipping subkit2 = subkits.stream().filter(subkit -> subkit.getKitTypeName().contains("RNA")).findFirst().orElseThrow();
            result = checkForKitErrors(subkit2, kitLabel, subkit2.getDdpLabel());
            if (!result.isEmpty()) {
                return result;
            }
            if (((subkit2.isKitRequiringTrackingScan() && subkit2.hasTrackingScan())
                    || (!subkit2.isKitRequiringTrackingScan()))) {
                //tracking scan needed and done OR no tracking scan needed
                //successfully scanned and going to update db and ES
                if ( StringUtils.isEmpty(subkit2.getKitLabel())) {
                    subkit1.setKitLabel(RNA);
                    subkit1.setDdpLabel(subkit2.getDdpLabel());
                    subkit1.setScanDate(System.currentTimeMillis());
                    result = updateKitRequest(subkit2);
                    trigerEventsIfSuccessfulKitUpdate(result, ddpLabel, subkit2);
                    this.writeSampleSentToES(subkit2);
                } else {
                    result = Optional.of(
                            new ScanError(subkit2.getDdpLabel(), "Designated RNA kit with Kit Label " + subkit2.getKitLabel() + " was scanned on Initial Scan page "));
                }
            } else {
                result = Optional.of(
                        new ScanError(ddpLabel, "DDP Label " + subkit2.getDdpLabel() + " requires tracking scan"));
                return result;
            }
        } else {
            //DSM label doesn't exist
            result = Optional.of(new ScanError(ddpLabel, "Kit with DSM Label " + ddpLabel + " does not exist"));
        }
        return result;
    }

    public Optional<ScanError> checkForKitErrors(KitRequestShipping kitRequestShipping, String kitLabel, String ddpLabel) {
        Optional<ScanError> result = Optional.empty();
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

        } else if (!((kitRequestShipping.isKitRequiringTrackingScan() && kitRequestShipping.hasTrackingScan())
                || (!kitRequestShipping.isKitRequiringTrackingScan()))) { // something is wrong about tracking scan
            if (kitRequestShipping.isKitRequiringTrackingScan() && !kitRequestShipping.hasTrackingScan()) {
                //tracking scan required and missing
                result = Optional.of(new ScanError(ddpLabel, "Kit with DSM Label " + ddpLabel + " does not have a Tracking Label"));
            } else {
                //wasn't saved
                result = Optional.of(new ScanError(ddpLabel, "Kit with DSM Label " + ddpLabel + " was not saved successfully"));
            }
        }
        return result;
    }
}
