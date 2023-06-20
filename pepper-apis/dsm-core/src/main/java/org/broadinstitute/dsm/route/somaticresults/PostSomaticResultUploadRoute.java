package org.broadinstitute.dsm.route.somaticresults;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.exception.AuthorizationException;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.model.somatic.result.SomaticResultMetaData;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.service.SomaticResultUploadService;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

@Slf4j
public class PostSomaticResultUploadRoute extends RequestHandler {
    private final SomaticResultUploadService service;
    private static final Logger logger = LoggerFactory.getLogger(PostSomaticResultUploadRoute.class);
    protected static final String REQUIRED_ROLE = "upload_ror_file";

    public PostSomaticResultUploadRoute(SomaticResultUploadService service) {
        this.service = service;
    }

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        Gson gson = new Gson();
        QueryParamsMap queryParams = request.queryMap();
        String realm = queryParams.get(RoutePath.REALM).value();
        String ddpParticipantId = queryParams.get(RoutePath.DDP_PARTICIPANT_ID).value();
        SomaticResultMetaData somaticResultMetaData;
        try {
            somaticResultMetaData = gson.fromJson(request.body(), SomaticResultMetaData.class);
        } catch (Exception ex) {
            throw new DSMBadRequestException("Bad request.  Are you passing the fileName, mimeType, and fileSize?");
        }
        if (!isValidRequest(realm, ddpParticipantId, userId, somaticResultMetaData)) {
            throw new AuthorizationException();
        }
        return service.authorizeUpload(realm, userId, ddpParticipantId, somaticResultMetaData);
    }

    private boolean isValidRequest(String realm, String ddpParticipantId, String userId, SomaticResultMetaData somaticResultMetaData) {
        logger.info("Requesting somatic documents for participant {} in realm {} by {}", ddpParticipantId, realm, userId);
        if (StringUtils.isBlank(realm)) {
            logger.warn("No realm provided in request for somatic documents by {} for {}", userId, ddpParticipantId);
            throw new DSMBadRequestException(RoutePath.REALM + " cannot be empty");
        }
        if (StringUtils.isBlank(ddpParticipantId)) {
            logger.warn("No participant id provided in request for somatic documents by {} in realm {}", userId, realm);
            throw new DSMBadRequestException(RoutePath.DDP_PARTICIPANT_ID + " cannot be empty");
        }
        if (StringUtils.isBlank(somaticResultMetaData.getFileName())) {
            logger.warn("File Name must be specified in request when uploading document by {} for {}", userId, realm);
            throw new DSMBadRequestException("fileName cannot be empty");
        }
        if (!isAuthorized(userId, realm)) {
            logger.warn("User {} is not authorized to access somatic documents in realm {}", userId, realm);
            return false;
        }
        return true;
    }

    private boolean isAuthorized(String userId, String realm) {
        return UserUtil.checkUserAccess(realm, userId, REQUIRED_ROLE);
    }
}
