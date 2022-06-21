package org.broadinstitute.dsm.route.mercury;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.mercury.MercuryOrderDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.mercury.MercuryOrderDummyRequest;
import org.broadinstitute.dsm.pubsub.MercuryOrderPublisher;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;
import spark.Route;

@Slf4j
public class PostMercuryOrderDummyRoute implements Route {
    private String projectId;
    private String topicId;
    private MercuryOrderPublisher mercuryOrderPublisher = new MercuryOrderPublisher(new MercuryOrderDao(), new ParticipantDao());

    public PostMercuryOrderDummyRoute(String projectId, String topicId) {
        this.projectId = projectId;
        this.topicId = topicId;
    }

    public void publishMessage(MercuryOrderDummyRequest mercuryOrderRequest, DDPInstanceDto ddpInstance, String userId) {
        log.info("Publishing message to Mercury");
        mercuryOrderPublisher
                .createAndPublishMessage(mercuryOrderRequest.getKitLabels(), projectId, topicId, ddpInstance,
                        mercuryOrderRequest.getCollaboratorParticipantId(), userId, null);
    }


    public Object handle(Request request, Response response) {
        String requestBody = request.body();
        QueryParamsMap queryParams = request.queryMap();
        String userId = "";
        if (queryParams.value(UserUtil.USER_ID) != null) {
            userId = queryParams.get(UserUtil.USER_ID).value();
        } else if (request.url().contains("/ddp")) {
            userId = "GP_UNIT_TEST";
        }
        MercuryOrderDummyRequest mercuryOrderRequest = new Gson().fromJson(requestBody, MercuryOrderDummyRequest.class);
        if (!isValidRequest(mercuryOrderRequest)) {
            log.error("Request not valid");
            return new Result(500, "Request body is not valid");
        }
        DDPInstanceDto ddpInstance = new DDPInstanceDao().getDDPInstanceByInstanceName(mercuryOrderRequest.getRealm()).orElseThrow();
        if (ddpInstance == null) {
            log.error("Realm was null for " + mercuryOrderRequest.getRealm());
            return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
        }
        publishMessage(mercuryOrderRequest, ddpInstance, userId);
        return new Result(200);

    }

    private boolean isValidRequest(MercuryOrderDummyRequest mercuryOrderRequest) {
        return (StringUtils.isNotBlank(mercuryOrderRequest.getCollaboratorParticipantId())) && mercuryOrderRequest.getKitLabels() != null
                && mercuryOrderRequest.getKitLabels().length > 0 && StringUtils.isNotBlank(mercuryOrderRequest.getRealm());
    }
}
