package org.broadinstitute.dsm.route;

import org.broadinstitute.dsm.model.stool.upload.StoolUploadService;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class StoolUploadRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(StoolUploadRoute.class);

    public static final String MF_BARCODE = "mfBarcode";
    public static final String RECEIVE_DATE = "receiveDate";
    public static final String KIT_UPLOAD = "kit_upload";

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        logger.info("Received a request for uploading a stool file into db");
        QueryParamsMap queryParams = request.queryMap();
        String realm;
        if (queryParams.hasKey(RoutePath.REALM)) {
            realm = queryParams.value(RoutePath.REALM);
        } else {
            throw new RuntimeException("The realm was absent");
        }
        if (userCanUploadKits(userId, realm)) {
            try {
                StoolUploadService.spawn().serve(request.body());
                response.status(200);
                return "Stool Upload was successful";
            } catch (Exception e) {
                response.status(500);
                return e.getMessage();
            }
        } else {
            response.status(500);
            return (UserErrorMessages.NO_RIGHTS);
        }
    }

    private static boolean userCanUploadKits(String userId, String realm) {
        return UserUtil.checkUserAccess(realm, userId, KIT_UPLOAD);
    }

}
