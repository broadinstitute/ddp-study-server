package org.broadinstitute.dsm.route.somaticresults;

import static spark.Spark.halt;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.service.SomaticResultUploadService;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class DeleteSomaticResultRoute extends RequestHandler {
    private final SomaticResultUploadService service;
    private static final Logger logger = LoggerFactory.getLogger(DeleteSomaticResultRoute.class);

    protected static final String REQUIRED_ROLE = "upload_ror_file";

    public DeleteSomaticResultRoute(SomaticResultUploadService uploadService) {
        this.service = uploadService;
    }

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();
        String realm = queryParams.get(RoutePath.REALM).value();
        String documentId = queryParams.get(RoutePath.SOMATIC_DOCUMENT_ID).value();
        if (isValidRequest(realm, documentId, userId)) {
            long userIdLong;
            int documentIdInt;
            try {
                userIdLong = Long.parseLong(userId);
                documentIdInt = Integer.parseInt(documentId);
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("Invalid user or document id.  Contact a DSM developer.", nfe);
            }
            return service.deleteUpload(userIdLong, documentIdInt, realm);
        }
        halt(500, UserErrorMessages.CONTACT_DEVELOPER);
        return null;
    }

    private boolean isValidRequest(String realm, String documentId, String userId) {
        logger.info("Requesting to delete somatic documents for id {} in realm {} by {}", documentId, realm, userId);
        if (StringUtils.isBlank(realm)) {
            logger.warn("No query parameter realm provided in request for somatic documents by {} for document id {}",
                    userId, documentId);
            throw new IllegalArgumentException(RoutePath.REALM + " cannot be empty");
        }
        if (StringUtils.isBlank(documentId)) {
            logger.warn("No query parameter somatic document id provided in request for somatic documents by {} in realm {}",
                    userId, realm);
            throw new IllegalArgumentException(RoutePath.SOMATIC_DOCUMENT_ID + " cannot be empty");
        }
        if (!isAuthorized(userId, realm)) {
            logger.warn("User {} is not authorized to access somatic documents in realm {}", userId, realm);
            halt(403, "Unauthorized to perform this action, contact a study official for authorization.");
            return false;
        }
        return true;
    }

    private boolean isAuthorized(String userId, String realm) {
        return UserUtil.checkUserAccess(realm, userId, REQUIRED_ROLE);
    }
}
