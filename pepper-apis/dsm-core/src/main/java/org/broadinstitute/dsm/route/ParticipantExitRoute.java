package org.broadinstitute.dsm.route;

import java.util.Collection;
import java.util.List;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.KitDiscard;
import org.broadinstitute.dsm.db.ParticipantExit;
import org.broadinstitute.dsm.exception.AuthorizationException;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.route.request.ParticipantExitRequest;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.service.participant.ParticipantExitService;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.UserUtil;
import spark.Request;
import spark.Response;

@Slf4j
public class ParticipantExitRoute extends RequestHandler {

    private static final String PARTICIPANT_EXIT_ROLE = "participant_exit";

    @Override
    public Object processRequest(Request request, Response response, String userId) {
        String requestMethod = request.requestMethod();
        if (requestMethod.equals(RoutePath.RequestMethod.GET.toString())) {
            String realm = request.params(RequestParameter.REALM);
            return handleGetRequest(realm, userId, new UserUtil(), new ParticipantExitService());
        } else if (requestMethod.equals(RoutePath.RequestMethod.POST.toString())) {
            String requestBody = RouteUtil.requireRequestBody(request);
            return handlePostRequest(requestBody, userId, new UserUtil(), new ParticipantExitService());
        }
        RouteUtil.handleInvalidRouteMethod(request, "ParticipantExitRoute");
        return null;
    }

    protected static Collection<ParticipantExit> handleGetRequest(String realm, String userId, UserUtil userUtil,
                                                                  ParticipantExitService participantExitService) {
        if (!userUtil.userHasRole(realm, userId, PARTICIPANT_EXIT_ROLE, null)) {
            log.warn("User {} not authorized to access realm {} with role {}", userId, realm, PARTICIPANT_EXIT_ROLE);
            throw new AuthorizationException();
        }
        return participantExitService.getExitedParticipants(realm).values();
    }

    protected static List<KitDiscard> handlePostRequest(String requestBody, String userId, UserUtil userUtil,
                                                        ParticipantExitService participantExitService) {
        ParticipantExitRequest req;
        try {
            req = new Gson().fromJson(requestBody, ParticipantExitRequest.class);
        } catch (Exception e) {
            log.info("Invalid request format for {}", requestBody);
            throw new DSMBadRequestException("Invalid request format");
        }
        String realm = req.getRealm().trim();
        if (StringUtils.isBlank(realm)) {
            throw new DSMBadRequestException("Realm is required");
        }

        if (!userUtil.userHasRole(realm, userId, PARTICIPANT_EXIT_ROLE, req.getUser())) {
            log.warn("User {} not authorized to access realm {} with role {}", userId, realm, PARTICIPANT_EXIT_ROLE);
            throw new AuthorizationException();
        }

        String ddpParticipantId = req.getParticipantId().trim();
        if (StringUtils.isBlank(ddpParticipantId)) {
            throw new DSMBadRequestException("Participant ID is required");
        }
        return participantExitService.exitParticipant(realm, ddpParticipantId, userId, req.isInDDP());
    }
}
