package org.broadinstitute.dsm.model.kit;

import org.broadinstitute.dsm.db.dao.ddp.kitrequest.KitRequestDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.kitrequest.KitRequestDto;
import org.broadinstitute.dsm.route.KitStatusChangeRoute;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.util.DSMConfig;

public class KitFinalScanUseCase extends BaseKitUseCase {

    public void finalScanCommand(String userId, DDPInstanceDto ddpInstanceDto) {
        String kit;
        String addValue;
        addValue = scan.getAsJsonObject().get("leftValue").getAsString();
        kit = scan.getAsJsonObject().get("rightValue").getAsString();
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
    protected void process() {

    }
}
