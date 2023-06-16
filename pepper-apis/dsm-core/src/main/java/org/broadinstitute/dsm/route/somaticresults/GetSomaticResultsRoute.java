package org.broadinstitute.dsm.route.somaticresults;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.exception.AuthorizationException;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.service.SomaticResultUploadService;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class GetSomaticResultsRoute extends RequestHandler  {
    private final SomaticResultUploadService service;
    private static final Logger logger = LoggerFactory.getLogger(GetSomaticResultsRoute.class);

    protected static final String REQUIRED_VIEW_ROLE = "view_shared_learnings";
    protected static final String REQUIRED_MANIPULATE_ROLE = "upload_ror_file";

    public GetSomaticResultsRoute(SomaticResultUploadService service) {
        this.service = service;
    }

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();
        String realm = queryParams.get(RoutePath.REALM).value();
        String ddpParticipantId = queryParams.get(RoutePath.DDP_PARTICIPANT_ID).value();
        if (isValidRequest(userId, realm, ddpParticipantId)) {
            return this.service.getSomaticResultsForParticipant(realm, ddpParticipantId);
        } else {
            throw new AuthorizationException();
        }
    }

    private boolean isValidRequest(String userId, String realm, String ddpParticipantId) {
        logger.info("Requesting somatic documents for participant {} in realm {} by {}", ddpParticipantId, realm, userId);
        if (StringUtils.isBlank(realm)) {
            logger.warn("No query parameter realm provided in request for somatic documents by {} for {}", userId, ddpParticipantId);
            throw new DSMBadRequestException(RoutePath.REALM + " cannot be empty");
        }
        if (StringUtils.isBlank(ddpParticipantId)) {
            logger.warn("No query parameter ddpParticipantId provided in request for somatic documents by {} in realm {}", userId, realm);
            throw new DSMBadRequestException(RoutePath.DDP_PARTICIPANT_ID + " cannot be empty");
        }
        return isAuthorized(userId, realm);
    }

    private boolean isAuthorized(String userId, String realm) {
        return UserUtil.checkUserAccess(realm, userId, REQUIRED_VIEW_ROLE)
                || UserUtil.checkUserAccess(realm, userId, REQUIRED_MANIPULATE_ROLE);
    }
}
