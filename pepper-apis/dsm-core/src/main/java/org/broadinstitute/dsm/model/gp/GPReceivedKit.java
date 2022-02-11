package org.broadinstitute.dsm.model.gp;

import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.InstanceSettings;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.kitrequest.KitRequestDao;
import org.broadinstitute.dsm.db.dao.kit.BSPKitDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.kit.BSPKitDto;
import org.broadinstitute.dsm.db.dto.settings.InstanceSettingsDto;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchDataUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class GPReceivedKit {

    private static Logger logger = LoggerFactory.getLogger(BSPKit.class);

    public static Optional<KitInfo> receiveKit(String kitLabel, BSPKitDto bspKitQueryResult, NotificationUtil notificationUtil) {
        logger.info("participant id is " + bspKitQueryResult.getDdpParticipantId());
        DDPInstance ddpInstance = DDPInstance.getDDPInstance(bspKitQueryResult.getInstanceName());
        InstanceSettings instanceSettings = new InstanceSettings();
        BSPKitDao bspKitDao = new BSPKitDao();
        InstanceSettingsDto instanceSettingsDto = instanceSettings.getInstanceSettings(bspKitQueryResult.getInstanceName());
        instanceSettingsDto.getKitBehaviorChange()
                .flatMap(kitBehavior -> kitBehavior.stream().filter(o -> o.getName().equals(InstanceSettings.INSTANCE_SETTING_RECEIVED)).findFirst())
                .ifPresentOrElse(received -> {
                    Map<String, Map<String, Object>> participants = ElasticSearchUtil.getFilteredDDPParticipantsFromES(ddpInstance,
                            ElasticSearchUtil.BY_GUID + bspKitQueryResult.getDdpParticipantId());
                    writeSampleReceivedToES(ddpInstance, bspKitQueryResult);
                    Map<String, Object> participant = participants.get(bspKitQueryResult.getDdpParticipantId());
                    if (participant != null) {
                        boolean triggerDDP = true;
                        boolean specialBehavior = InstanceSettings.shouldKitBehaveDifferently(participant, received);
                        if (specialBehavior) {
                            //don't trigger ddp to sent out email, only email to study staff
                            triggerDDP = false;
                            if (InstanceSettings.TYPE_NOTIFICATION.equals(received.getType())) {
                                String message = "Kit of participant " + bspKitQueryResult.getBspParticipantId() + " was received by GP. <br> " +
                                        "CollaboratorSampleId:  " + bspKitQueryResult.getBspSampleId() + " <br> " +
                                        received.getValue();
                                notificationUtil.sentNotification(bspKitQueryResult.getNotificationRecipient(), message, NotificationUtil.UNIVERSAL_NOTIFICATION_TEMPLATE, NotificationUtil.DSM_SUBJECT);
                            } else {
                                logger.error("Instance settings behavior for kit was not known " + received.getType());
                            }
                        }
                        updateKitAndExport(kitLabel, bspKitDao, bspKitQueryResult, triggerDDP);
                    }
                }, () -> {
                    updateKitAndExport(kitLabel, bspKitDao, bspKitQueryResult, true);
                });

        String bspParticipantId = bspKitQueryResult.getBspParticipantId();
        String bspSampleId = bspKitQueryResult.getBspSampleId();
        String bspMaterialType = bspKitQueryResult.getBspMaterialType();
        String bspReceptacleType = bspKitQueryResult.getBspReceptacleType();
        int bspOrganism;
        try {
            bspOrganism = Integer.parseInt(bspKitQueryResult.getBspOrganism());
        } catch (NumberFormatException e) {
            throw new RuntimeException("Organism " + bspKitQueryResult.getBspOrganism() + " can't be parsed to integer", e);
        }

        logger.info("Returning info for kit w/ label " + kitLabel + " for " + bspKitQueryResult.getInstanceName());
        logger.info("Kit returned has sample id " + bspSampleId);
        return Optional.of(new KitInfo(bspKitQueryResult.getBspCollection(),
                bspOrganism,
                "U",
                bspParticipantId,
                bspSampleId,
                bspMaterialType,
                bspReceptacleType,
                ddpInstance.getName(),
                bspKitQueryResult.getKitTypeName()));

    }

    private static void updateKitAndExport(String kitLabel, BSPKitDao bspKitDao, BSPKitDto maybeBspKitQueryResult, boolean triggerDDP) {
        long receivedDate = System.currentTimeMillis();
        bspKitDao.setKitReceivedAndTriggerDDP(kitLabel, triggerDDP, maybeBspKitQueryResult);

        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        kitRequestShipping.setReceiveDate(receivedDate);
        kitRequestShipping.setKitLabel(kitLabel);

        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(maybeBspKitQueryResult.getInstanceName()).orElseThrow();

        UpsertPainlessFacade.of(DBConstants.DDP_KIT_REQUEST_ALIAS, kitRequestShipping, ddpInstanceDto, ESObjectConstants.KIT_LABEL,
                        ESObjectConstants.KIT_LABEL, kitLabel)
                .export();
    }

    private static void writeSampleReceivedToES(DDPInstance ddpInstance, BSPKitDto bspKitInfo) {
        String kitRequestId = new KitRequestDao().getKitRequestIdByBSPParticipantId(bspKitInfo.getBspParticipantId());
        Map<String, Object> nameValuesMap = new HashMap<>();
        ElasticSearchDataUtil.setCurrentStrictYearMonthDay(nameValuesMap, ESObjectConstants.RECEIVED);
        ElasticSearchUtil.writeSample(ddpInstance, kitRequestId, bspKitInfo.getDdpParticipantId(), ESObjectConstants.SAMPLES,
                ESObjectConstants.KIT_REQUEST_ID, nameValuesMap);
    }
}
