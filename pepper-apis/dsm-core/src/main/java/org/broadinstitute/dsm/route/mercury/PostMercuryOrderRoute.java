package org.broadinstitute.dsm.route.mercury;

import java.util.List;
import java.util.Optional;

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
import org.broadinstitute.dsm.util.UserUtil;
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
        String realm = Optional.ofNullable(queryParams.get(RoutePath.REALM).value())
                .orElseThrow(() -> new RuntimeException("No realm query param was sent"));
        String ddpParticipantId = Optional.ofNullable(queryParams.get(RoutePath.DDP_PARTICIPANT_ID).value())
                .orElseThrow(() -> new RuntimeException("No DDP_PARTICIPANT_ID query param was sent"));
        String userIdRequest = UserUtil.getUserId(request);
        if (!UserUtil.checkUserAccess(realm, userId, "kit_sequencing_order", userIdRequest)) {
            log.warn("User doesn't have access " + userId);
            response.status(500);
            return UserErrorMessages.NO_RIGHTS;
        }
        MercurySampleDto[] mercurySampleDtos = new Gson().fromJson(requestBody, MercurySampleDto[].class);
        DDPInstanceDto ddpInstance = new DDPInstanceDao().getDDPInstanceByInstanceName(realm).orElseThrow();
        publishMessage(mercurySampleDtos, ddpInstance, userId, ddpParticipantId);
        response.status(200);
        return "";
    }

    public void publishMessage(MercurySampleDto[] mercurySampleDtos, DDPInstanceDto ddpInstance, String userId, String ddpParticipantId) {
        log.info("Preparing message to publish to Mercury");
        List<String> barcodes = new MercuryOrderUseCase().collectBarcodes(mercurySampleDtos);
        String[] barcodesArray = barcodes.toArray(new String[barcodes.size()]);
        mercuryOrderPublisher
                .createAndPublishMessage(barcodesArray, projectId, topicId, ddpInstance,
                        null, userId, ddpParticipantId);
    }
}
