package org.broadinstitute.dsm.route.mercury;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.mercury.MercuryOrderDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.mercury.MercuryOrderDummyRequest;
import org.broadinstitute.dsm.pubsub.MercuryOrderPublisher;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;
import spark.Route;

@Slf4j
public class PostMercuryOrderDummyRoute implements Route {
    private String projectId;
    private String topicId;
    private MercuryOrderPublisher mercuryOrderPublisher = new MercuryOrderPublisher(new MercuryOrderDao(), new ParticipantDao());
    private static final String PEPPER_ORDER_ID = "PepperOrderId";

    public PostMercuryOrderDummyRoute(String projectId, String topicId) {
        this.projectId = projectId;
        this.topicId = topicId;
    }

    public String publishMessage(MercuryOrderDummyRequest mercuryOrderRequest, DDPInstanceDto ddpInstance, String userId) {
        log.info("Publishing message to Mercury");
        return mercuryOrderPublisher
                .createAndPublishMessage(mercuryOrderRequest.getKitLabels(), projectId, topicId, ddpInstance,
                        mercuryOrderRequest.getCollaboratorParticipantId(), userId, null);
    }


    public Object handle(Request request, Response response) {
        String requestBody = request.body();
        QueryParamsMap queryParams = request.queryMap();
        MercuryOrderDummyRequest mercuryOrderRequest = new Gson().fromJson(requestBody, MercuryOrderDummyRequest.class);
        if (!isValidRequest(mercuryOrderRequest)) {
            log.error("Request not valid");
            response.status(500);
            return "Request body is not valid";
        }
        DDPInstanceDto ddpInstance = new DDPInstanceDao().getDDPInstanceByInstanceName(mercuryOrderRequest.getRealm()).orElseThrow();
        if (ddpInstance == null) {
            log.error("Realm was null for " + mercuryOrderRequest.getRealm());
            response.status(500);
            return UserErrorMessages.CONTACT_DEVELOPER;
        }
        String pepperOrderId = publishMessage(mercuryOrderRequest, ddpInstance, "DUMMY_ROUTE");
        JSONObject main = new JSONObject();
        main.put(PEPPER_ORDER_ID, pepperOrderId);
        return main;
    }

    private boolean isValidRequest(MercuryOrderDummyRequest mercuryOrderRequest) {
        return (StringUtils.isNotBlank(mercuryOrderRequest.getCollaboratorParticipantId())) && mercuryOrderRequest.getKitLabels() != null
                && mercuryOrderRequest.getKitLabels().length > 0 && StringUtils.isNotBlank(mercuryOrderRequest.getRealm());
    }
}
