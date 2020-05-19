package org.broadinstitute.ddp.filter;

import static spark.Spark.halt;

import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
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
public class OnlyStudyAdminFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(OnlyStudyAdminFilter.class);
    private static final AuthPathRegexUtil pathMatcher = new AuthPathRegexUtil();

    @Override
    public void handle(Request request, Response response) {
        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        if (ddpAuth.getToken() == null || ddpAuth.getOperator() == null) {
            LOG.error("Expects study admin operator but no auth token found in request");
            halt(401);
            return;
        }

        boolean canAccess = ddpAuth.isAdmin();

        String path = request.pathInfo();
        if (pathMatcher.isStudyRoute(path)) {
            // If it's a study-specific route, make sure study admin has access to this particular study.
            String studyGuid = request.params(PathParam.STUDY_GUID);
            canAccess = ddpAuth.hasAdminAccessToStudy(studyGuid);
        }

        if (!canAccess) {
            LOG.error("User tried accessing study admin only route, operator={}, method={}, path={}",
                    ddpAuth.getOperator(), request.requestMethod(), path);
            halt(401);
        }
    }
}
