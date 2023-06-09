package org.broadinstitute.dsm.route;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.service.onchistory.CodeStudyColumnsProvider;
import org.broadinstitute.dsm.service.onchistory.OncHistoryUploadService;
import org.broadinstitute.dsm.service.onchistory.OncHistoryValidationException;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

@Slf4j
public class OncHistoryUploadRoute extends RequestHandler {

    public static final String ONC_HISTORY_UPLOAD = "onc_history_upload";

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
            response.status(401);
            return (UserErrorMessages.NO_RIGHTS);
        }

        try {
            OncHistoryUploadService service =
                    new OncHistoryUploadService(realm, userId, new CodeStudyColumnsProvider());
            service.upload(request.body());
            response.status(200);
            // TODO: needed?
            return "Onc history upload succeeded";
        } catch (DSMBadRequestException | OncHistoryValidationException e) {
            response.status(400);
            return e.getMessage();
        } catch (DsmInternalError e) {
            response.status(500);
            return e.getMessage();
        } catch (Exception e) {
            // TODO in some future day we are not throwing exceptions that we do not have a mapped status code
            response.status(500);
            return e.getMessage();
        }
    }

    private static boolean canUploadOncHistory(String realm, String userId) {
        // TODO: fix role
        return UserUtil.checkUserAccess(realm, userId, ONC_HISTORY_UPLOAD);
    }
}
