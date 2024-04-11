package org.broadinstitute.dsm.model.kit;

import java.util.Optional;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.model.KitDDPNotification;
import org.broadinstitute.dsm.model.elastic.export.painless.PutToNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.EventUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class KitFinalSentBaseUseCase extends BaseKitUseCase {

    private static final Logger logger = LoggerFactory.getLogger(KitFinalSentBaseUseCase.class);

    private static final String GET_SENT_KIT_INFORMATION_FOR_NOTIFICATION_EMAIL =
            "select eve.event_name, eve.event_type, "
                    + "request.ddp_participant_id, request.dsm_kit_request_id, "
                    + "request.ddp_kit_request_id, request.upload_reason, realm.ddp_instance_id, "
                    + "realm.instance_name, realm.base_url, realm.auth0_token, realm.notification_recipients, "
                    + "realm.migrated_ddp, kit.receive_date, kit.scan_date from ddp_kit_request request, "
                    + "ddp_kit kit, event_type eve, ddp_instance realm where request.dsm_kit_request_id = kit.dsm_kit_request_id "
                    + "and request.ddp_instance_id = realm.ddp_instance_id and "
                    + "(eve.ddp_instance_id = request.ddp_instance_id and eve.kit_type_id = request.kit_type_id) "
                    + "and eve.event_type = \"SENT\" and request.ddp_label = ?";



    public KitFinalSentBaseUseCase(KitPayload kitPayload, KitDao kitDao) {
        super(kitPayload, kitDao);
    }

    protected void trigerEventsIfSuccessfulKitUpdate(Optional<ScanResult> result, String ddpLabel,
                                                     KitRequestShipping kitRequestShipping) {
        if (isKitUpdateSuccessful(result, kitRequestShipping.getBspCollaboratorParticipantId())) {
            triggerEvents(ddpLabel, kitRequestShipping);
        }
    }

    protected void triggerEvents(String ddpLabel, KitRequestShipping kitRequestShipping) {
        logger.info("Updated kitRequests w/ ddp_label " + kitRequestShipping.getDdpLabel());
        KitDDPNotification kitDDPNotification = KitDDPNotification.getKitDDPNotification(GET_SENT_KIT_INFORMATION_FOR_NOTIFICATION_EMAIL,
                ddpLabel, 1);
        if (kitDDPNotification != null) {
            TransactionWrapper.inTransaction(conn -> {
                // TODO: this should not be in a DB transaction since it makes a call to an external service -DC
                EventUtil.triggerDDP(conn, kitDDPNotification);
                return null;
            });
        }
        if (kitPayload.getDdpInstanceDto().isESUpdatePossible()) {
            try {
                UpsertPainlessFacade.of(DBConstants.DDP_KIT_REQUEST_ALIAS, kitRequestShipping,
                        kitPayload.getDdpInstanceDto(), "ddpLabel", "ddpLabel",
                        ddpLabel, new PutToNestedScriptBuilder()).export();
            } catch (Exception e) {
                logger.error(String.format("Error updating ddp label for kit with ddpLabel: %s", ddpLabel));
                e.printStackTrace();
            }
        }
    }
}
