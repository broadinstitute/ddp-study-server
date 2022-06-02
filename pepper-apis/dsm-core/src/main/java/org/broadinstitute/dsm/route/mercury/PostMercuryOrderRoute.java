package org.broadinstitute.dsm.route.mercury;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.mercury.MercuryOrderDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.mercury.MercuryOrderRequest;
import org.broadinstitute.dsm.pubsub.MercuryOrderPublisher;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.Request;
import spark.Response;
import spark.Route;

@Slf4j
public class PostMercuryOrderRoute implements Route {
    private String projectId;
    private String topicId;
    private MercuryOrderPublisher mercuryOrderPublisher = new MercuryOrderPublisher(new MercuryOrderDao(), new ParticipantDao());

    public PostMercuryOrderRoute(String projectId, String topicId) {
        this.projectId = projectId;
        this.topicId = topicId;
    }

    public void publishMessage(MercuryOrderRequest mercuryOrderRequest, DDPInstanceDto ddpInstance) {
        log.info("Publishing message to Mercury");
        mercuryOrderPublisher
                .createAndPublishMessage(mercuryOrderRequest.getKitLabels(), projectId, topicId, ddpInstance,
                        mercuryOrderRequest.getDdpParticipantId(), mercuryOrderRequest.getCollaboratorParticipantId());
    }


    public Object handle(Request request, Response response) {
        String requestBody = request.body();
        MercuryOrderRequest mercuryOrderRequest = new Gson().fromJson(requestBody, MercuryOrderRequest.class);
        if (!isValidRequest(mercuryOrderRequest)) {
            log.error("Request not valid");
            return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
        }
        DDPInstanceDto ddpInstance = new DDPInstanceDao().getDDPInstanceByInstanceName(mercuryOrderRequest.getRealm()).orElseThrow();
        if (ddpInstance == null) {
            log.error("Realm was null for " + mercuryOrderRequest.getRealm());
            return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
        }
        publishMessage(mercuryOrderRequest, ddpInstance);
        return new Result(200);

    }

    private boolean isValidRequest(MercuryOrderRequest mercuryOrderRequest) {
        return (StringUtils.isNotBlank(mercuryOrderRequest.getCollaboratorParticipantId())
                || StringUtils.isNotBlank(mercuryOrderRequest.getDdpParticipantId())) && mercuryOrderRequest.getKitLabels() != null
                && mercuryOrderRequest.getKitLabels().length > 0 && StringUtils.isNotBlank(mercuryOrderRequest.getRealm());
    }
}
