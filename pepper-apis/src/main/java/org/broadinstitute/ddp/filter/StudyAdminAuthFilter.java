package org.broadinstitute.ddp.filter;

import static spark.Spark.halt;

import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.util.RouteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Filter;
import spark.Request;
import spark.Response;

/**
 * A filter that only allows access to study admins.
 */
public class StudyAdminAuthFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(StudyAdminAuthFilter.class);
    private static final AuthPathRegexUtil pathMatcher = new AuthPathRegexUtil();

    @Override
    public void handle(Request request, Response response) {
        if (request.requestMethod().equals("OPTIONS")) {
            return;
        }

        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        if (ddpAuth.getToken() == null || ddpAuth.getOperator() == null) {
            LOG.error("Expects study admin operator but no auth token found in request");
            halt(401);
            return;
        }

        boolean canAccess = ddpAuth.isAdmin();

        String path = request.pathInfo();
        if (pathMatcher.isAdminStudyRoute(path)) {
            // If it's a study-specific route, make sure study admin has access to this particular study.
            String studyGuid = RouteUtil.parseAdminStudyGuid(path);
            if (studyGuid == null) {
                throw new DDPException("Unable to parse admin study guid from request uri path: " + path);
            }
            canAccess = ddpAuth.hasAdminAccessToStudy(studyGuid);
        }

        if (!canAccess) {
            LOG.error("User tried accessing study admin only route, operator={}, method={}, path={}",
                    ddpAuth.getOperator(), request.requestMethod(), path);
            halt(401);
        }
    }
}
