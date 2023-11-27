package org.broadinstitute.dsm.route;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.exception.AuthorizationException;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.service.onchistory.CodeStudyColumnsProvider;
import org.broadinstitute.dsm.service.onchistory.OncHistoryUploadService;
import org.broadinstitute.dsm.service.onchistory.OncHistoryValidationException;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.Request;
import spark.Response;

@Slf4j
public class OncHistoryUploadRoute extends RequestHandler {

    public static final String ONC_HISTORY_UPLOAD = "upload_onc_history";

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        String realm = RouteUtil.requireParam(request, RoutePath.REALM);

        if (!canUploadOncHistory(realm, userId)) {
            throw new AuthorizationException("User is not authorized to upload onc history");
        }

        String oncHistoryUserId = RouteUtil.getUserEmail(userId);
        try {
            OncHistoryUploadService service =
                    new OncHistoryUploadService(realm, oncHistoryUserId, new CodeStudyColumnsProvider());
            service.upload(request.body());
            return new Result(200);
        } catch (DSMBadRequestException | OncHistoryValidationException e) {
            response.status(400);
            log.info("Bad request for onc history upload: {}", e.toString());
            return e.getMessage();
        } catch (DsmInternalError e) {
            response.status(500);
            log.info("Internal error processing onc history upload: {}", e.toString());
            return e.getMessage();
        } catch (Exception e) {
            // TODO in some future day we are not throwing exceptions that we do not have a mapped status code
            log.warn("Unhandled exception processing onc history upload: {}", e.toString());
            response.status(500);
            return e.getMessage();
        }
    }

    protected static boolean canUploadOncHistory(String realm, String userId) {
        return UserUtil.checkUserAccess(realm, userId, ONC_HISTORY_UPLOAD);
    }
}
