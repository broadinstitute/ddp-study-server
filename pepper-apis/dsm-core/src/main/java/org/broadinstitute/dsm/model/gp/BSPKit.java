package org.broadinstitute.dsm.model.gp;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.dao.kit.BSPKitDao;
import org.broadinstitute.dsm.db.dto.kit.BSPKitDto;
import org.broadinstitute.dsm.model.KitDDPNotification;
import org.broadinstitute.dsm.model.gp.bsp.BSPKitStatus;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.util.EventUtil;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.Optional;

public class BSPKit extends GPReceivedKit {

    private static Logger logger = LoggerFactory.getLogger(BSPKit.class);

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

    public void triggerDDP(Connection conn, @NonNull BSPKitDto bspKitInfo, boolean firstTimeReceived, String kitLabel) {
        try {
            if (bspKitInfo.isHasParticipantNotifications() && firstTimeReceived) {
                KitDDPNotification kitDDPNotification = KitDDPNotification.getKitDDPNotification(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GET_RECEIVED_KIT_INFORMATION_FOR_NOTIFICATION_EMAIL), kitLabel, 1);
                if (kitDDPNotification != null) {
                    EventUtil.triggerDDP(conn, kitDDPNotification);
                }
            }
        } catch (Exception e) {
            logger.error("Failed doing DSM internal received things for kit w/ label " + kitLabel, e);
        }
    }
}
