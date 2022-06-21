package org.broadinstitute.dsm.route.mercury;

import java.util.ArrayList;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.mercury.MercuryOrderDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.mercury.MercuryOrderUseCase;
import org.broadinstitute.dsm.db.dto.mercury.MercurySampleDto;
import org.broadinstitute.dsm.pubsub.MercuryOrderPublisher;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

@Slf4j
public class PostMercuryOrderRoute extends RequestHandler {

    private String projectId;
    private String topicId;
    private MercuryOrderPublisher mercuryOrderPublisher = new MercuryOrderPublisher(new MercuryOrderDao(), new ParticipantDao());

    public PostMercuryOrderRoute(String projectId, String topicId) {
        this.projectId = projectId;
        this.topicId = topicId;
    }

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        String requestBody = request.body();
        QueryParamsMap queryParams = request.queryMap();
        String realm;
        if (queryParams.value(RoutePath.REALM) != null) {
            realm = queryParams.get(RoutePath.REALM).value();
        } else {
            throw new RuntimeException("No realm query param was sent");
        }
        String ddpParticipantId;
        if (queryParams.value(RoutePath.DDP_PARTICIPANT_ID) != null) {
            ddpParticipantId = queryParams.get(RoutePath.DDP_PARTICIPANT_ID).value();
        } else {
            throw new RuntimeException("No realm query param was sent");
        }
        //todo check user permissions
        MercurySampleDto[] mercurySampleDtos = new Gson().fromJson(requestBody, MercurySampleDto[].class);
        DDPInstanceDto ddpInstance = new DDPInstanceDao().getDDPInstanceByInstanceName(realm).orElseThrow();
        if (ddpInstance == null) {
            log.error("Realm was null for " + realm);
            return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
        }
        publishMessage(mercurySampleDtos, ddpInstance, userId, ddpParticipantId);
        return new Result(200);
    }

    public void publishMessage(MercurySampleDto[] mercurySampleDtos, DDPInstanceDto ddpInstance, String userId, String ddpParticipantId) {
        log.info("Preparing message to publish to Mercury");
        ArrayList<String> barcodes = MercuryOrderUseCase.createBarcodes(mercurySampleDtos, ddpInstance);
        String[] barcodesArray = barcodes.toArray(new String[barcodes.size()]);
        mercuryOrderPublisher
                .createAndPublishMessage(barcodesArray, projectId, topicId, ddpInstance,
                        null, userId, ddpParticipantId);
    }
}
