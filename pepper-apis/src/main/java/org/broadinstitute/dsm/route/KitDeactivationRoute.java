package org.broadinstitute.dsm.route;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.InstanceSettings;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dto.settings.InstanceSettingsDto;
import org.broadinstitute.dsm.model.Value;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.KitUtil;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

import java.util.Map;

public class KitDeactivationRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(KitDeactivationRoute.class);

    private NotificationUtil notificationUtil;

    public KitDeactivationRoute(@NonNull NotificationUtil notificationUtil) {
        this.notificationUtil = notificationUtil;
    }

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        String kitRequestId = request.params(RequestParameter.KITREQUESTID);
        if (StringUtils.isNotBlank(kitRequestId)) {
            String userIdRequest =  UserUtil.getUserId(request);
            boolean deactivate = request.url().toLowerCase().contains("deactivate");
            KitRequestShipping kitRequest = KitRequestShipping.getKitRequest(kitRequestId);
            String realm = kitRequest.getRealm();
            if ( UserUtil.checkUserAccess(realm, userId, "kit_deactivation", userIdRequest)) {
                if (deactivate) {
                    JsonObject jsonObject = new JsonParser().parse(request.body()).getAsJsonObject();
                    String reason = jsonObject.get("reason").getAsString();
                    KitRequestShipping.deactivateKitRequest(kitRequestId, reason, DSMServer.getDDPEasypostApiKey(realm), userIdRequest);
                }
                else {
                    QueryParamsMap queryParams = request.queryMap();
                    boolean activateAnyway = false;
                    if (queryParams.value("activate") != null) {
                        activateAnyway = queryParams.get("activate").booleanValue();
                    }
                    if (activateAnyway) {
                        KitRequestShipping.reactivateKitRequest(kitRequestId, KitUtil.IGNORE_AUTO_DEACTIVATION);
                    }
                    else {
                        DDPInstance ddpInstance = DDPInstance.getDDPInstance(realm);
                        InstanceSettings instanceSettings = new InstanceSettings();
                        InstanceSettingsDto instanceSettingsDto = instanceSettings.getInstanceSettings(realm);
                        Value activation = instanceSettingsDto
                                .getKitBehaviorChange()
                                .map(kitBehavior -> kitBehavior.stream().filter(o -> o.getName().equals(InstanceSettings.INSTANCE_SETTING_ACTIVATION)).findFirst().orElse(null))
                                .orElse(null);

                        if (activation != null && StringUtils.isNotBlank(ddpInstance.getParticipantIndexES())) {
                            Map<String, Map<String, Object>> participants = ElasticSearchUtil.getFilteredDDPParticipantsFromES(ddpInstance,
                                    ElasticSearchUtil.BY_GUID + kitRequest.getParticipantId());
                            Map<String, Object> participant = participants.get(kitRequest.getParticipantId());
                            boolean specialBehavior = InstanceSettings.shouldKitBehaveDifferently(participant, activation);
                            if (specialBehavior) {
                                if (InstanceSettings.TYPE_ALERT.equals(activation.getType())) {
                                    return new Result(200, activation.getValue());
                                }
                                else if (InstanceSettings.TYPE_NOTIFICATION.equals(activation.getType())) {
                                    String message = "Kit for participant " + kitRequest.getParticipantId() + " was activated <br>" + activation.getValue();
                                    notificationUtil.sentNotification(ddpInstance.getNotificationRecipient(), message, NotificationUtil.UNIVERSAL_NOTIFICATION_TEMPLATE, NotificationUtil.DSM_SUBJECT);
                                }
                                else {
                                    logger.error("Instance settings behavior for kit was not known " + activation.getType());
                                }
                            }
                            else {
                                KitRequestShipping.reactivateKitRequest(kitRequestId);
                            }
                        }
                        else {
                            KitRequestShipping.reactivateKitRequest(kitRequestId);
                        }
                    }

                }
                return new Result(200);
            }
            else {
                response.status(500);
                return new Result(500, UserErrorMessages.NO_RIGHTS);
            }
        }
        else {
            throw new RuntimeException("KitRequestId id was missing");
        }
    }
}
