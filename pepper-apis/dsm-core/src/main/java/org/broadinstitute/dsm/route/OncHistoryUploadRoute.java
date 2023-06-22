package org.broadinstitute.dsm.route;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.service.onchistory.CodeStudyColumnsProvider;
import org.broadinstitute.dsm.service.onchistory.OncHistoryUploadService;
import org.broadinstitute.dsm.service.onchistory.OncHistoryValidationException;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

@Slf4j
public class OncHistoryUploadRoute extends RequestHandler {

    public static final String ONC_HISTORY_UPLOAD = "kit_upload"; // TOOO temp until testers are part of role -DC
    //public static final String ONC_HISTORY_UPLOAD = "onc_history_upload";

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();
        String realm;
        if (!queryParams.hasKey(RoutePath.REALM)) {
            response.status(400);
            return "Request requires realm parameter";
        }
        realm = queryParams.value(RoutePath.REALM);

        if (!canUploadOncHistory(realm, userId)) {
            response.status(403);
            return (UserErrorMessages.NO_RIGHTS);
        }

        String oncHistoryUserId;
        try {
            UserDto user = new UserDao().get(Integer.parseInt(userId)).orElseThrow();
            oncHistoryUserId = user.getEmail().orElseThrow();
            if (oncHistoryUserId.isEmpty()) {
                throw new DsmInternalError("Empty email address");
            }
        } catch (Exception e) {
            response.status(500);
            return "No email address for user " + userId;
        }

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

    private static boolean canUploadOncHistory(String realm, String userId) {
        return UserUtil.checkUserAccess(realm, userId, ONC_HISTORY_UPLOAD);
    }
}
