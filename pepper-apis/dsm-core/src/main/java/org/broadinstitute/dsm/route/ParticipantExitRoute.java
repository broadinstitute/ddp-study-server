package org.broadinstitute.dsm.route;

import com.google.api.client.http.HttpStatusCodes;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitDiscard;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.ParticipantExit;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.exception.ParticipantNotExist;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.DDPRequestUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ParticipantExitRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(ParticipantExitRoute.class);

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        String requestBody = request.body();
        String realm = request.params(RequestParameter.REALM);
        if (StringUtils.isNotBlank(realm)) {
            if (UserUtil.checkUserAccess(realm, userId, "participant_exit", null)) {
                return ParticipantExit.getExitedParticipants(realm).values();
            }
            else {
                response.status(500);
                return new Result(500, UserErrorMessages.NO_RIGHTS);
            }
        }

        if (requestBody != null) {
            long currentTime = System.currentTimeMillis();
            JsonObject jsonObject = new JsonParser().parse(request.body()).getAsJsonObject();
            realm = jsonObject.getAsJsonObject().get("realm").getAsString();
            if (UserUtil.checkUserAccess(realm, userId, "participant_exit", null)) {
                String ddpParticipantId = jsonObject.getAsJsonObject().get("participantId").getAsString();
                String userIdRequest = jsonObject.getAsJsonObject().get("user").getAsString();
                boolean inDDP = jsonObject.getAsJsonObject().get("inDDP").getAsBoolean();

                try {
                    exitParticipant(realm, ddpParticipantId, userIdRequest, currentTime, inDDP);
                    List<KitRequestShipping> kitRequests = KitRequestShipping.getKitRequestsByParticipant(realm, ddpParticipantId, true);
                    List<KitDiscard> kitsNeedAction = new ArrayList<>();
                    logger.info("Found " + kitRequests.size() + " kit requests");
                    Optional<DDPInstanceDto> maybeInstance = new DDPInstanceDao().getDDPInstanceByInstanceName(realm);
                    for (KitRequestShipping kit : kitRequests) {
                        if (kit.getScanDate() != 0 && kit.getReceiveDate() == 0) {
                            String discardId = KitDiscard.addKitToDiscard(kit.getDsmKitRequestId(), KitDiscard.HOLD);
                            kitsNeedAction.add(new KitDiscard(discardId, kit.getKitTypeName(), KitDiscard.HOLD));
                        }
                        else {
                            //refund label of kits which are not sent yet
                            KitRequestShipping.refundKit(kit.getDsmKitRequestId(), DSMServer.getDDPEasypostApiKey(realm), maybeInstance.orElse(null));
                        }
                    }
                    return kitsNeedAction;
                }
                catch (ParticipantNotExist e) {
                    logger.error("DDP didn't find participant w/ ddpParticipantId " + ddpParticipantId);
                    return new Result(404, e.getMessage());
                }
            }
            else {
                response.status(500);
                return new Result(500, UserErrorMessages.NO_RIGHTS);
            }
        }
        logger.error("Request body was missing");
        return new Result(500);
    }

    private void exitParticipant(@NonNull String realm, @NonNull String ddpParticipantId, @NonNull String userId,
                                 @NonNull long currentTime, boolean inDDP) {
        if (StringUtils.isNotBlank(realm) && StringUtils.isNotBlank(ddpParticipantId) && StringUtils.isNotBlank(userId)) {
            DDPInstance instance = DDPInstance.getDDPInstanceWithRole(realm, DBConstants.HAS_EXIT_PARTICIPANT_ENDPOINT);
            if (instance.isHasRole()) {
                String sendRequest = instance.getBaseUrl() + RoutePath.DDP_PARTICIPANT_EXIT_PATH + "/" + ddpParticipantId;
                Integer response = null;
                try {
                    response = DDPRequestUtil.postRequest(sendRequest, null, instance.getName(), instance.isHasAuth0Token());
                }
                catch (Exception e) {
                    throw new RuntimeException("Couldn't exit participant " + sendRequest, e);
                }
                if (response == HttpStatusCodes.STATUS_CODE_OK) {
                    logger.info("Triggered DDP to exit participant w/ ddpParticipantId " + ddpParticipantId);
                    ParticipantExit.exitParticipant(ddpParticipantId, currentTime, userId, instance, inDDP);
                }
                else if (response == HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
                    throw new ParticipantNotExist("Participant not found");
                }
            }
            else {
                ParticipantExit.exitParticipant(ddpParticipantId, currentTime, userId, instance, false);
            }
        }
    }
}
