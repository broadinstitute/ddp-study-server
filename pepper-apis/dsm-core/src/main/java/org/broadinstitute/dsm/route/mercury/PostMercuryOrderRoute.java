package org.broadinstitute.dsm.route.mercury;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.mercury.MercuryOrderDao;
import org.broadinstitute.dsm.pubsub.MercuryOrderPublisher;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

@Slf4j
public class PostMercuryOrderRoute extends RequestHandler {
    private static String projectId;
    private static String topicId;
    private static MercuryOrderPublisher mercuryOrderPublisher = new MercuryOrderPublisher(new MercuryOrderDao());

    public PostMercuryOrderRoute(String projectId, String topicId) {
        this.projectId = projectId;
        this.topicId = topicId;
    }

    public static void publishMessage(MercuryOrderRequest mercuryOrderRequest, DDPInstance ddpInstance)
            throws Exception {
        log.info("Publishing message to Mercury");
        mercuryOrderPublisher
                .publishMessage(mercuryOrderRequest.kitLabels, projectId, topicId, ddpInstance, mercuryOrderRequest.ddpParticipantId);
    }


    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        String userIdRequest = UserUtil.getUserId(request);
        QueryParamsMap queryParams = request.queryMap();
        String realm;
        if (queryParams.value(RoutePath.REALM) != null) {
            realm = queryParams.get(RoutePath.REALM).value();
        } else {
            throw new RuntimeException("No realm query param was sent");
        }

        if (StringUtils.isNotBlank(realm)) {
            DDPInstance ddpInstance = DDPInstance.getDDPInstance(realm);
            if (RoutePath.RequestMethod.POST.toString().equals(request.requestMethod())) {
                if (UserUtil.checkUserAccess(realm, userId, "mercury_order", userIdRequest)) {
                    String requestBody = request.body();
                    MercuryOrderRequest mercuryOrderRequest = new Gson().fromJson(requestBody, MercuryOrderRequest.class);
                    if (StringUtils.isNotBlank(mercuryOrderRequest.ddpParticipantId) && mercuryOrderRequest.kitLabels != null
                            && mercuryOrderRequest.kitLabels.length > 0) {
                        publishMessage(mercuryOrderRequest, ddpInstance);
                    }
                } else {
                    response.status(500);
                    return new Result(500, UserErrorMessages.NO_RIGHTS);
                }
            }
            log.error("Request method not known");
            return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
        }
        log.error("realm was empty");
        return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
    }
}
