package org.broadinstitute.dsm.route.mercury;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.mercury.MercuryOrderDao;
import org.broadinstitute.dsm.model.mercury.MercuryOrderRequest;
import org.broadinstitute.dsm.pubsub.MercuryOrderPublisher;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.Request;
import spark.Response;
import spark.Route;

@Slf4j
public class PostMercuryOrderRoute implements Route {
    private static String projectId;
    private static String topicId;
    private static MercuryOrderPublisher mercuryOrderPublisher = new MercuryOrderPublisher(new MercuryOrderDao());

    public PostMercuryOrderRoute(String projectId, String topicId) {
        this.projectId = projectId;
        this.topicId = topicId;
    }

    public static void publishMessage(MercuryOrderRequest mercuryOrderRequest, DDPInstance ddpInstance) {
        log.info("Publishing message to Mercury");
        mercuryOrderPublisher
                .createAndPublishMessage(mercuryOrderRequest.getKitLabels(), projectId, topicId, ddpInstance,
                        mercuryOrderRequest.getDdpParticipantId());
    }


    public Object handle(Request request, Response response) throws Exception {
        if (RoutePath.RequestMethod.POST.toString().equals(request.requestMethod())) {
            String requestBody = request.body();
            MercuryOrderRequest mercuryOrderRequest = new Gson().fromJson(requestBody, MercuryOrderRequest.class);
            if (StringUtils.isNotBlank(mercuryOrderRequest.getDdpParticipantId()) && mercuryOrderRequest.getKitLabels() != null
                    && mercuryOrderRequest.getKitLabels().length > 0 && StringUtils.isNotBlank(mercuryOrderRequest.getRealm())) {
                DDPInstance ddpInstance = DDPInstance.getDDPInstance(mercuryOrderRequest.getRealm());
                if (ddpInstance != null) {
                    publishMessage(mercuryOrderRequest, ddpInstance);
                    return new Result(200);
                } else {
                    log.error("Realm was empty");
                    return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
                }
            }
            log.error("Request method not known");
            return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
        }
        log.error("realm was empty");
        return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
    }
}
