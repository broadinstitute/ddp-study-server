package org.broadinstitute.dsm.route;

import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.Assignee;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import spark.Request;
import spark.Response;

public class AssigneeRoute extends RequestHandler {

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        String realm = RoutePath.getRealm(request);
        String userIdRequest = UserUtil.getUserId(request);
        if (UserUtil.checkUserAccess(realm, userId, "mr_view", userIdRequest)) {
            return Assignee.getAssignees(realm);
        }
        else {
            response.status(500);
            return new Result(500, UserErrorMessages.NO_RIGHTS);
        }
    }
}
