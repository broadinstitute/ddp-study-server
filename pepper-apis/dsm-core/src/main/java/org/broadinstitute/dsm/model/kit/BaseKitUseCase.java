package org.broadinstitute.dsm.model.kit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import lombok.AllArgsConstructor;
import lombok.Setter;
import org.broadinstitute.dsm.db.KitRequestShipping;
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

@AllArgsConstructor
@Setter
public abstract class BaseKitUseCase implements Supplier<List<KitStatusChangeRoute.ScanError>> {

    private static final Logger logger = LoggerFactory.getLogger(BaseKitUseCase.class);


    protected KitPayload kitPayload;

    @Override
    public List<KitStatusChangeRoute.ScanError> get() {
        List<KitStatusChangeRoute.ScanError> result = new ArrayList<>();
        for (ScanPayload scanPayload: kitPayload.getScanPayloads()) {
            process(scanPayload).ifPresent(result::add);
        }
        return result;
    }

    protected abstract Optional<KitStatusChangeRoute.ScanError> process(ScanPayload scanPayload);

    protected boolean isKitUpdateSuccessful(Optional<KitStatusChangeRoute.ScanError> maybeScanError) {
        return maybeScanError.isEmpty();
    }

    protected void triggerEvents(String kit, KitRequestShipping kitRequestShipping) {
        .logger.info("Updated kitRequests w/ ddp_label " + kitRequestShipping.getDdpLabel());
        KitDDPNotification kitDDPNotification = KitDDPNotification.getKitDDPNotification(
                DSMConfig.getSqlFromConfig(ApplicationConfigConstants.GET_SENT_KIT_INFORMATION_FOR_NOTIFICATION_EMAIL), kit,
                1);
        if (kitDDPNotification != null) {
            EventUtil.triggerDDP(conn, kitDDPNotification);
        }
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
