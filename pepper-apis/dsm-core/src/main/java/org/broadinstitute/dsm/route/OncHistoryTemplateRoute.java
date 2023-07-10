package org.broadinstitute.dsm.route;

import static org.broadinstitute.dsm.route.OncHistoryUploadRoute.canUploadOncHistory;

import java.util.zip.ZipOutputStream;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.service.onchistory.CodeStudyColumnsProvider;
import org.broadinstitute.dsm.service.onchistory.OncHistoryTemplateService;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

@Slf4j
public class OncHistoryTemplateRoute extends RequestHandler {

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

        try {
            ZipOutputStream zos = new ZipOutputStream(response.raw().getOutputStream());
            OncHistoryTemplateService service =
                    new OncHistoryTemplateService(realm, new CodeStudyColumnsProvider());
            service.writeTemplate(zos);
            return new Result(200);
        } catch (DSMBadRequestException e) {
            response.status(400);
            log.info("Bad request for onc history template: {}", e.toString());
            return e.getMessage();
        } catch (DsmInternalError e) {
            response.status(500);
            log.info("Internal error processing onc history template request: {}", e.toString());
            return e.getMessage();
        } catch (Exception e) {
            // TODO in some future day we are not throwing exceptions that we do not have a mapped status code
            log.warn("Unhandled exception processing onc history template request: {}", e.toString());
            response.status(500);
            return e.getMessage();
        }
    }
}
