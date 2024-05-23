package org.broadinstitute.dsm.model.gp;

import java.util.Optional;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.kit.BSPKitDao;
import org.broadinstitute.dsm.db.dto.kit.BSPKitDto;
import org.broadinstitute.dsm.model.KitDDPNotification;
import org.broadinstitute.dsm.model.gp.bsp.BSPKitStatus;
import org.broadinstitute.dsm.service.EventService;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BSPKit extends GPReceivedKit {

    private static Logger logger = LoggerFactory.getLogger(BSPKit.class);
    EventService eventService = new EventService();

    public static final String  SQL_SELECT_KIT_INFO_FOR_NOTIFICATION_EMAIL = "select eve.event_name,  eve.event_type,"
            + " request.ddp_participant_id, request.dsm_kit_request_id, request.ddp_kit_request_id, request.upload_reason, "
            + " realm.ddp_instance_id, realm.instance_name, realm.base_url, realm.auth0_token, realm.notification_recipients, "
            + " realm.migrated_ddp, kit.receive_date, kit.scan_date from ddp_kit_request request, ddp_kit kit, event_type eve, "
            + " ddp_instance realm where request.dsm_kit_request_id = kit.dsm_kit_request_id and "
            + " request.ddp_instance_id = realm.ddp_instance_id and (eve.ddp_instance_id = request.ddp_instance_id "
            + " and eve.kit_type_id = request.kit_type_id) and eve.event_type = 'RECEIVED' and kit.kit_label = ?";

    public Optional<BSPKitStatus> getKitStatus(@NonNull BSPKitDto bspKitQueryResult, NotificationUtil notificationUtil) {
        if (StringUtils.isNotBlank(bspKitQueryResult.getParticipantExitId())) {
            String message = "Kit of exited participant " + bspKitQueryResult.getBspParticipantId() + " was received by GP.<br>";
            notificationUtil.sentNotification(bspKitQueryResult.getNotificationRecipient(), message, NotificationUtil.DSM_SUBJECT);
            return Optional.of(new BSPKitStatus(BSPKitStatus.EXITED));
        } else if (StringUtils.isNotBlank(bspKitQueryResult.getDeactivationDate())) {
            return Optional.of(new BSPKitStatus(BSPKitStatus.DEACTIVATED));
        }
        return Optional.empty();
    }

    public Optional<BSPKitDto> canReceiveKit(@NonNull String kitLabel) {
        BSPKitDao bspKitDao = new BSPKitDao();
        Optional<BSPKitDto> bspKitQueryResult = bspKitDao.getBSPKitQueryResult(kitLabel);
        if (bspKitQueryResult.isEmpty()) {
            logger.info("No kit w/ label " + kitLabel + " found");
            return Optional.empty();
        } else {
            return bspKitQueryResult;
        }
    }

    public void triggerDDP(@NonNull BSPKitDto bspKitInfo, boolean firstTimeReceived, String kitLabel) {
        try {
            if (bspKitInfo.isHasParticipantNotifications() && firstTimeReceived) {
                KitDDPNotification kitDDPNotification = KitDDPNotification.getKitDDPNotification(SQL_SELECT_KIT_INFO_FOR_NOTIFICATION_EMAIL,
                        kitLabel, 1);
                if (kitDDPNotification != null) {
                    eventService.sendKitEventToDss(kitDDPNotification);
                }
            }
        } catch (Exception e) {
            logger.error("Failed doing DSM internal received things for kit w/ label " + kitLabel, e);
        }
    }
}
