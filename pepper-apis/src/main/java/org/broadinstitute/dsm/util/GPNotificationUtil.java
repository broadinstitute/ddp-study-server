package org.broadinstitute.dsm.util;

import com.typesafe.config.Config;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.email.Recipient;
import org.broadinstitute.dsm.model.KitDDPSummary;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GPNotificationUtil {

    private static final Logger logger = LoggerFactory.getLogger(GPNotificationUtil.class);

    public static final String EMAIL_TYPE = "GP_NOTIFICATION";

    public static final String KITREQUEST_LINK = "/permalink/shipping?target=queue";

    private String gpNotificationRecipient;

    private String frontendUrl;

    private NotificationUtil notificationUtil;
    private KitUtil kitUtil;

    public GPNotificationUtil(@NonNull Config config, @NonNull NotificationUtil notificationUtil,
                              @NonNull KitUtil kitUtil) {
        this.gpNotificationRecipient = config.getString(ApplicationConfigConstants.EMAIL_GP_RECIPIENT);
        this.frontendUrl =  config.getString(ApplicationConfigConstants.EMAIL_FRONTEND_URL_FOR_LINKS);
        this.notificationUtil = notificationUtil;
        this.kitUtil = kitUtil;

        if (StringUtils.isBlank(gpNotificationRecipient)) {
            throw new RuntimeException("gpNotificationRecipient is required");
        }
    }

    /**
     * Query for not shipped kit requests and write notification information into EMAIL_QUEUE
     */
    public void queryAndWriteNotification() {
        logger.info("GP notification job");
        HashMap<String, String> unsentExpressKits = kitUtil.getUnsentExpressKits( true);
        List<KitDDPSummary> unsentKits = KitDDPSummary.getUnsentKits(false, null);
        if (!unsentKits.isEmpty() && !unsentExpressKits.isEmpty()) {
            String message = "";
            for (String realm : unsentExpressKits.keySet()) {
                String value = unsentExpressKits.get(realm);
                if (!"0".equals(value)) {
                    if (realm.contains("_")) {
                        String[] key = realm.split("_");
                        message += key[0] + " has " + value + " express " + key[1] + " kit requests @ Queue.<br>";
                    }
                    else {
                        message += realm + " has " + value + " express kit requests @ Queue.<br>";
                    }
                }
            }
            for (KitDDPSummary kitDDPSummary : unsentKits) {
                if (kitDDPSummary != null) {
                    if (!"0".equals(kitDDPSummary.getKitsNoLabel())) {
                        message += kitDDPSummary.getRealm() + " has " + kitDDPSummary.getKitsNoLabel() + " unsent " + kitDDPSummary.getKitType() + " kit requests (with no label yet)<br>";
                    }
                    if (!"0".equals(kitDDPSummary.getKitsQueue())) {
                        message += kitDDPSummary.getRealm() + " has " + kitDDPSummary.getKitsQueue() + " unsent " + kitDDPSummary.getKitType() + " kit requests @ Queue.<br>";
                    }
                    if (!"0".equals(kitDDPSummary.getKitsError())) {
                        message += kitDDPSummary.getRealm() + " has " + kitDDPSummary.getKitsError() + " unsent " + kitDDPSummary.getKitType() + " kit requests @ Error.<br>";
                    }
                }
            }
            if (StringUtils.isNotBlank(message)) {
                Map<String, String> mapy = new HashMap<>();
                mapy.put(":customText", message);
                Recipient emailRecipient = new Recipient(gpNotificationRecipient);
                emailRecipient.setUrl(frontendUrl + KITREQUEST_LINK);
                emailRecipient.setSurveyLinks(mapy);

                notificationUtil.queueCurrentAndFutureEmails(EMAIL_TYPE, emailRecipient, EMAIL_TYPE);
            }
            else {
                logger.info("No (express/normal) kits are in queue/error, skipping GP notification");
            }
        }
    }
}
