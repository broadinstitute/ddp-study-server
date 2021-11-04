package org.broadinstitute.dsm.route;

import com.google.gson.Gson;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.Drug;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class DrugListRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(DrugListRoute.class);

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        if (RoutePath.RequestMethod.GET.toString().equals(request.requestMethod())) {
            return Drug.getDrugListALL();
        }
        String userIdRequest = UserUtil.getUserId(request);
        if (RoutePath.RequestMethod.PATCH.toString().equals(request.requestMethod())) {
            if (UserUtil.checkUserAccess(null, userId, "drug_list_edit", userIdRequest)) {
                String requestBody = request.body();
                Drug drugUpdateValues = new Gson().fromJson(requestBody, Drug.class);
                if (drugUpdateValues != null) {
                    Drug.addDrug(userId, drugUpdateValues);
                    return new Result(200);
                }
            }
            else {
                response.status(500);
                return new Result(500, UserErrorMessages.NO_RIGHTS);
            }
        }
        logger.error("Request method not known");
        return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
    }
}
