package org.broadinstitute.dsm.model;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.InstanceSettings;
import org.broadinstitute.dsm.db.dao.ddp.kitrequest.KitRequestDao;
import org.broadinstitute.dsm.db.dao.kit.BSPKitDao;
import org.broadinstitute.dsm.db.dto.kit.BSPKitDto;
import org.broadinstitute.dsm.db.dto.settings.InstanceSettingsDto;
import org.broadinstitute.dsm.model.bsp.BSPKitInfo;
import org.broadinstitute.dsm.model.bsp.BSPKitStatus;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchDataUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.EventUtil;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class BSPKit {
    private static Logger logger = LoggerFactory.getLogger(BSPKit.class);

    public Optional<BSPKitStatus> getKitStatus(@NonNull String kitLabel, NotificationUtil notificationUtil) {
        logger.info("Checking label " + kitLabel);
        BSPKitDao bspKitDao = new BSPKitDao();
        Optional<BSPKitDto> bspKitQueryResult = bspKitDao.getBSPKitQueryResult(kitLabel);
        Optional<BSPKitStatus> result = Optional.empty();
        if (bspKitQueryResult.isEmpty()) {
            logger.info("No kit w/ label " + kitLabel + " found");
        }
        else {
            BSPKitDto maybeBspKitQueryResult = bspKitQueryResult.get();
            if (StringUtils.isNotBlank(maybeBspKitQueryResult.getParticipantExitId())) {
                String message = "Kit of exited participant " + maybeBspKitQueryResult.getBspParticipantId() + " was received by GP.<br>";
                notificationUtil.sentNotification(maybeBspKitQueryResult.getNotificationRecipient(), message, NotificationUtil.DSM_SUBJECT);
                result = Optional.of(new BSPKitStatus(BSPKitStatus.EXITED));
            }
            else if (StringUtils.isNotBlank(maybeBspKitQueryResult.getDeactivationDate())) {
                result = Optional.of(new BSPKitStatus(BSPKitStatus.DEACTIVATED));
            }
        }
        return result;
    }

    public boolean canReceiveKit(@NonNull String kitLabel) {
        BSPKitDao bspKitDao = new BSPKitDao();
        Optional<BSPKitDto> bspKitQueryResult = bspKitDao.getBSPKitQueryResult(kitLabel);
        if (bspKitQueryResult.isEmpty()) {
            logger.info("No kit w/ label " + kitLabel + " found");
            return false;
        }
        else {
            BSPKitDto maybeBspKitQueryResult = bspKitQueryResult.get();
            if (StringUtils.isNotBlank(maybeBspKitQueryResult.getParticipantExitId()) || StringUtils.isNotBlank(maybeBspKitQueryResult.getDeactivationDate())) {
                logger.info("Kit can not be received.");
                return false;
            }
        }
        return true;
    }

    public Optional<BSPKitInfo> receiveBSPKit(String kitLabel, NotificationUtil notificationUtil) {
        logger.info("Trying to receive kit " + kitLabel);
        BSPKitDao bspKitDao = new BSPKitDao();
        Optional<BSPKitDto> bspKitQueryResult = bspKitDao.getBSPKitQueryResult(kitLabel);
        if (bspKitQueryResult.isEmpty()) {
            logger.warn("returning empty object for "+kitLabel);
            return Optional.empty();
        }
        BSPKitDto maybeBspKitQueryResult = bspKitQueryResult.get();
        if (StringUtils.isBlank(maybeBspKitQueryResult.getDdpParticipantId())) {
            throw new RuntimeException("No participant id for " + kitLabel + " from " + maybeBspKitQueryResult.getInstanceName());
        }
        logger.info("particpant id is " + maybeBspKitQueryResult.getDdpParticipantId());
        DDPInstance ddpInstance = DDPInstance.getDDPInstance(maybeBspKitQueryResult.getInstanceName());
        InstanceSettings instanceSettings = new InstanceSettings();
        InstanceSettingsDto instanceSettingsDto = instanceSettings.getInstanceSettings(maybeBspKitQueryResult.getInstanceName());
        instanceSettingsDto.getKitBehaviorChange()
                .flatMap(kitBehavior -> kitBehavior.stream().filter(o -> o.getName().equals(InstanceSettings.INSTANCE_SETTING_RECEIVED)).findFirst())
                .ifPresentOrElse(received -> {
                    Map<String, Map<String, Object>> participants = ElasticSearchUtil.getFilteredDDPParticipantsFromES(ddpInstance,
                            ElasticSearchUtil.BY_GUID + maybeBspKitQueryResult.getDdpParticipantId());
                    writeSampleReceivedToES(ddpInstance, maybeBspKitQueryResult);
                    Map<String, Object> participant = participants.get(maybeBspKitQueryResult.getDdpParticipantId());
                    if (participant != null) {
                        boolean triggerDDP = true;
                        boolean specialBehavior = InstanceSettings.shouldKitBehaveDifferently(participant, received);
                        if (specialBehavior) {
                            //don't trigger ddp to sent out email, only email to study staff
                            triggerDDP = false;
                            if (InstanceSettings.TYPE_NOTIFICATION.equals(received.getType())) {
                                String message = "Kit of participant " + maybeBspKitQueryResult.getBspParticipantId() + " was received by GP. <br> " +
                                        "CollaboratorSampleId:  " + maybeBspKitQueryResult.getBspSampleId() + " <br> " +
                                        received.getValue();
                                notificationUtil.sentNotification(maybeBspKitQueryResult.getNotificationRecipient(), message, NotificationUtil.UNIVERSAL_NOTIFICATION_TEMPLATE, NotificationUtil.DSM_SUBJECT);
                            }
                            else {
                                logger.error("Instance settings behavior for kit was not known " + received.getType());
                            }
                        }
                        bspKitDao.setKitReceivedAndTriggerDDP(kitLabel, triggerDDP, maybeBspKitQueryResult);
                    }
                }, () -> {
                    bspKitDao.setKitReceivedAndTriggerDDP(kitLabel, true, maybeBspKitQueryResult);
                });

        String bspParticipantId = maybeBspKitQueryResult.getBspParticipantId();
        String bspSampleId = maybeBspKitQueryResult.getBspSampleId();
        String bspMaterialType = maybeBspKitQueryResult.getBspMaterialType();
        String bspReceptacleType = maybeBspKitQueryResult.getBspReceptacleType();
        int bspOrganism;
        try {
            bspOrganism = Integer.parseInt(maybeBspKitQueryResult.getBspOrganism());
        }
        catch (NumberFormatException e) {
            throw new RuntimeException("Organism " + maybeBspKitQueryResult.getBspOrganism() + " can't be parsed to integer", e);
        }

        logger.info("Returning info for kit w/ label " + kitLabel + " for " + maybeBspKitQueryResult.getInstanceName());
        logger.info("Kit returned has sample id " + bspSampleId);
        return Optional.of(new BSPKitInfo(maybeBspKitQueryResult.getBspCollection(),
                bspOrganism,
                "U",
                bspParticipantId,
                bspSampleId,
                bspMaterialType,
                bspReceptacleType));

    }

    private void writeSampleReceivedToES(DDPInstance ddpInstance, BSPKitDto bspKitInfo) {
        String kitRequestId = new KitRequestDao().getKitRequestIdByBSPParticipantId(bspKitInfo.getBspParticipantId());
        Map<String, Object> nameValuesMap = new HashMap<>();
        ElasticSearchDataUtil.setCurrentStrictYearMonthDay(nameValuesMap, ESObjectConstants.RECEIVED);
        ElasticSearchUtil.writeSample(ddpInstance, kitRequestId, bspKitInfo.getDdpParticipantId(), ESObjectConstants.SAMPLES,
                ESObjectConstants.KIT_REQUEST_ID, nameValuesMap);
    }

    public void triggerDDP(Connection conn, @NonNull BSPKitDto bspKitInfo, boolean firstTimeReceived, String kitLabel) {
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
