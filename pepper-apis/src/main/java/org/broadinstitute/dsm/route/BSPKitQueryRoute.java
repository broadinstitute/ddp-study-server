package org.broadinstitute.dsm.route;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.InstanceSettings;
import org.broadinstitute.dsm.model.Value;
import org.broadinstitute.dsm.model.bsp.BSPKitQueryResult;
import org.broadinstitute.dsm.model.bsp.BSPKitInfo;
import org.broadinstitute.dsm.model.bsp.BSPKitStatus;
import org.broadinstitute.dsm.model.KitDDPNotification;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.EventUtil;
import org.broadinstitute.dsm.util.KitUtil;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

import java.sql.Connection;
import java.util.*;

public class BSPKitQueryRoute implements Route {

    private static final Logger logger = LoggerFactory.getLogger(BSPKitQueryRoute.class);

    private NotificationUtil notificationUtil;

    public BSPKitQueryRoute(@NonNull NotificationUtil notificationUtil) {
        this.notificationUtil = notificationUtil;
    }

    @Override
    public Object handle(Request request, Response response) throws Exception {
        String kitLabel = request.params(RequestParameter.LABEL);
        if (StringUtils.isBlank(kitLabel)) {
            throw new RuntimeException("Please include a kit label as a path parameter");
        }

        return TransactionWrapper.inTransaction(conn -> {
            return getBSPKitInfo(conn, kitLabel, response);
        });
    }

    public Object getBSPKitInfo(Connection conn, @NonNull String kitLabel, @NonNull Response response) {
        logger.info("Checking label " + kitLabel);
        BSPKitQueryResult bspKitInfo = BSPKitQueryResult.getBSPKitQueryResult(kitLabel);

        if (bspKitInfo == null) {
            logger.info("No kit w/ label " + kitLabel + " found");
            response.status(404);
            return null;
        }

        boolean firstTimeReceived = KitUtil.setKitReceived(conn, kitLabel);
        if (StringUtils.isNotBlank(bspKitInfo.getParticipantExitId())) {
            String message = "Kit of exited participant " + bspKitInfo.getBspParticipantId() + " was received by GP.<br>";
            notificationUtil.sentNotification(bspKitInfo.getNotificationRecipient(), message, NotificationUtil.DSM_SUBJECT);
            return new BSPKitStatus(BSPKitStatus.EXITED);
        }
        if (StringUtils.isNotBlank(bspKitInfo.getDeactivationDate())) {
            return new BSPKitStatus(BSPKitStatus.DEACTIVATED);
        }
        if (StringUtils.isNotBlank(bspKitInfo.getDdpParticipantId())) {
            DDPInstance ddpInstance = DDPInstance.getDDPInstance(bspKitInfo.getInstanceName());
            InstanceSettings instanceSettings = InstanceSettings.getInstanceSettings(bspKitInfo.getInstanceName());
            Value received = null;
            if (instanceSettings != null && instanceSettings.getKitBehaviorChange() != null) {
                List<Value> kitBehavior = instanceSettings.getKitBehaviorChange();
                try {
                    received = kitBehavior.stream().filter(o -> o.getName().equals(InstanceSettings.INSTANCE_SETTING_RECEIVED)).findFirst().get();
                }
                catch (NoSuchElementException e) {
                    received = null;
                }
            }

            if (received != null && StringUtils.isNotBlank(ddpInstance.getParticipantIndexES())) {
                Map<String, Map<String, Object>> participants = ElasticSearchUtil.getFilteredDDPParticipantsFromES(ddpInstance,
                    ElasticSearchUtil.BY_GUID + bspKitInfo.getDdpParticipantId());
                Map<String, Object> participant = participants.get(bspKitInfo.getDdpParticipantId());
                if (participant != null) {
                    boolean specialBehavior = InstanceSettings.shouldKitBehaveDifferently(participant, received);
                    if (specialBehavior) {
                        //don't trigger ddp to sent out email, only email to study staff
                        if (InstanceSettings.TYPE_NOTIFICATION.equals(received.getType())) {
                            String message = "Kit of participant " + bspKitInfo.getBspParticipantId() + " was received by GP. <br> " +
                                    "CollaboratorSampleId:  " + bspKitInfo.getBspSampleId() + " <br> " +
                                    received.getValue();
                            notificationUtil.sentNotification(bspKitInfo.getNotificationRecipient(), message, NotificationUtil.UNIVERSAL_NOTIFICATION_TEMPLATE, NotificationUtil.DSM_SUBJECT);
                        }
                        else {
                            logger.error("Instance settings behavior for kit was not known " + received.getType());
                        }
                    }
                    else {
                        triggerDDP(conn, bspKitInfo, firstTimeReceived, kitLabel);
                    }
                }
            }
            else {
                triggerDDP(conn, bspKitInfo, firstTimeReceived, kitLabel);
            }

            String bspParticipantId = bspKitInfo.getBspParticipantId();
            String bspSampleId = bspKitInfo.getBspSampleId();
            String bspMaterialType = bspKitInfo.getBspMaterialType();
            String bspReceptacleType = bspKitInfo.getBspReceptacleType();
            int bspOrganism;
            try {
                bspOrganism = Integer.parseInt(bspKitInfo.getBspOrganism());
            }
            catch (NumberFormatException e) {
                throw new RuntimeException("Organism " + bspKitInfo.getBspOrganism() + " can't be parsed to integer", e);
            }

            logger.info("Returning info for kit w/ label " + kitLabel + " for " + bspKitInfo.getInstanceName());
            return new BSPKitInfo(bspKitInfo.getBspCollection(),
                    bspOrganism,
                    "U",
                    bspParticipantId,
                    bspSampleId,
                    bspMaterialType,
                    bspReceptacleType);
        }
        else {
            throw new RuntimeException("No participant id for " + kitLabel + " from " + bspKitInfo.getInstanceName());
        }
    }

    private void triggerDDP(Connection conn, @NonNull BSPKitQueryResult bspKitInfo, boolean firstTimeReceived, String kitLabel) {
        try {
            if (bspKitInfo.isHasParticipantNotifications() && firstTimeReceived) {
                KitDDPNotification kitDDPNotification = KitDDPNotification.getKitDDPNotification(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GET_RECEIVED_KIT_INFORMATION_FOR_NOTIFICATION_EMAIL), kitLabel, 1);
                if (kitDDPNotification != null) {
                    EventUtil.triggerDDP(conn, kitDDPNotification);
                }
            }
        }
        catch (Exception e) {
            logger.error("Failed doing DSM internal received things for kit w/ label " + kitLabel, e);
        }
    }
}
