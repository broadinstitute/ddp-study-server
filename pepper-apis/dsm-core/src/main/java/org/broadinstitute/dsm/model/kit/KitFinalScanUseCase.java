package org.broadinstitute.dsm.model.kit;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.kitrequest.KitRequestDao;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.kitrequest.KitRequestDto;
import org.broadinstitute.dsm.model.KitDDPNotification;
import org.broadinstitute.dsm.model.elastic.export.painless.PutToNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.route.KitPayload;
import org.broadinstitute.dsm.route.KitStatusChangeRoute;
import org.broadinstitute.dsm.route.ScanPayload;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.DSMConfig;
import org.broadinstitute.dsm.util.EventUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KitFinalScanUseCase extends BaseKitUseCase {

    private static final Logger logger = LoggerFactory.getLogger(KitFinalScanUseCase.class);

    private KitDao kitDao;

    public KitFinalScanUseCase(KitPayload kitPayload, KitDao kitDao) {
        super(kitPayload);
        this.kitDao = kitDao;
    }

    public void finalScanCommand(String userId, DDPInstanceDto ddpInstanceDto) {
        String addValue = scan.getAsJsonObject().get("leftValue").getAsString();
        String kit = scan.getAsJsonObject().get("rightValue").getAsString();
        //check if ddp_label is blood kit
        if (checkKitLabel(DSMConfig.getSqlFromConfig(ApplicationConfigConstants.GET_KIT_TYPE_NEED_TRACKING_BY_DDP_LABEL), kit)) {
            //check if kit_label is in tracking table
            if (checkKitLabel(
                    DSMConfig.getSqlFromConfig(ApplicationConfigConstants.GET_FOUND_IF_KIT_LABEL_ALREADY_EXISTS_IN_TRACKING_TABLE),
                    addValue)) {
                updateKit(changeType, kit, addValue, currentTime, scanErrorList, userId, ddpInstanceDto);
                KitRequestDao kitRequestDao = new KitRequestDao();
                KitRequestDto kitRequestByLabel = kitRequestDao.getKitRequestByLabel(kit);
                if (kitRequestByLabel != null) {
                    KitStatusChangeRoute.writeSampleSentToES(kitRequestByLabel);
                }
            } else {
                scanErrorList.add(new KitStatusChangeRoute.ScanError(kit, "Kit with DSM Label \"" + kit + "\" does not have a Tracking Label"));
            }
        } else {
            updateKit(changeType, kit, addValue, currentTime, scanErrorList, userId, ddpInstanceDto);
        }
    }

    @Override
    protected KitStatusChangeRoute.ScanError process(ScanPayload scanPayload) {
        String addValue = scanPayload.getAddValue();
        String kit = scanPayload.getKit();
        //check if ddp_label is blood kit
        if (kitDao.isBloodKit(kit)) {
            //check if kit_label is in tracking table
            if (kitDao.hasTrackingScan(addValue)) {
                KitRequestShipping kitRequestShipping = new KitRequestShipping();
                kitRequestShipping.setKitLabel(addValue);
                kitRequestShipping.setDdpLabel(kit);
                Integer updatedRows = kitDao.updateKitRequest(kitRequestShipping, userId);
                if (updatedRows > 0) {
                    logger.info("Updated kitRequests w/ ddp_label " + kitRequestShipping.getDdpLabel());
                    KitDDPNotification kitDDPNotification = KitDDPNotification.getKitDDPNotification(
                            DSMConfig.getSqlFromConfig(ApplicationConfigConstants.GET_SENT_KIT_INFORMATION_FOR_NOTIFICATION_EMAIL), kit,
                            1);
                    if (kitDDPNotification != null) {
                        EventUtil.triggerDDP(conn, kitDDPNotification);
                    }
                    try {
                        UpsertPainlessFacade.of(DBConstants.DDP_KIT_REQUEST_ALIAS, kitRequestShipping, ddpInstanceDto, "ddpLabel",
                                "ddpLabel", kit, new PutToNestedScriptBuilder()).export();
                    } catch (Exception e) {
                        logger.error(String.format("Error updating ddp label for kit with label: %s", kit));
                        e.printStackTrace();
                    }
                }
                KitRequestDao kitRequestDao = new KitRequestDao();
                kitRequestDao.getKitRequestByLabel(kit).ifPresent(KitStatusChangeRoute::writeSampleSentToES);
            } else {
                scanErrorList.add(new KitStatusChangeRoute.ScanError(kit, "Kit with DSM Label \"" + kit + "\" does not have a Tracking Label"));
            }
        } else {
            updateKit(changeType, kit, addValue, currentTime, scanErrorList, (String) null, (DDPInstanceDto) null);
        }
    }
}
