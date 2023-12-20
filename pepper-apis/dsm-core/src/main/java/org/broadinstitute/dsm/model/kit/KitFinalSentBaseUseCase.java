package org.broadinstitute.dsm.model.kit;

import java.util.Optional;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.model.KitDDPNotification;
import org.broadinstitute.dsm.model.elastic.export.painless.PutToNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.DSMConfig;
import org.broadinstitute.dsm.util.EventUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class KitFinalSentBaseUseCase extends BaseKitUseCase {

    private static final Logger logger = LoggerFactory.getLogger(KitFinalSentBaseUseCase.class);

    public KitFinalSentBaseUseCase(KitPayload kitPayload, KitDao kitDao) {
        super(kitPayload, kitDao);
    }

    protected void trigerEventsIfSuccessfulKitUpdate(Optional<ScanResult> result, String kit,
                                                     KitRequestShipping kitRequestShipping) {
        if (isKitUpdateSuccessful(result, kitRequestShipping.getBspCollaboratorParticipantId())) {
            triggerEvents(kit, kitRequestShipping);
        }
    }

    protected void triggerEvents(String kit, KitRequestShipping kitRequestShipping) {
        logger.info("Updated kitRequests w/ ddp_label " + kitRequestShipping.getDdpLabel());
        KitDDPNotification kitDDPNotification = KitDDPNotification.getKitDDPNotification(
                DSMConfig.getSqlFromConfig(ApplicationConfigConstants.GET_SENT_KIT_INFORMATION_FOR_NOTIFICATION_EMAIL), kit,
                1);
        if (kitDDPNotification != null) {
            TransactionWrapper.inTransaction(conn -> {
                EventUtil.triggerDDP(conn, kitDDPNotification);
                return null;
            });
        }
        if (kitPayload.getDdpInstanceDto().isESUpdatePossible()) {
            try {
                UpsertPainlessFacade.of(DBConstants.DDP_KIT_REQUEST_ALIAS, kitRequestShipping,
                        kitPayload.getDdpInstanceDto(), "ddpLabel", "ddpLabel",
                        kit, new PutToNestedScriptBuilder()).export();
            } catch (Exception e) {
                logger.error(String.format("Error updating ddp label for kit with label: %s", kit));
                e.printStackTrace();
            }
        }
    }
}
