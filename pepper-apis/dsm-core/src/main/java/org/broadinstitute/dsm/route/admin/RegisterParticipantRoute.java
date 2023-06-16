package org.broadinstitute.dsm.route.admin;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.Study;
import org.broadinstitute.dsm.model.defaultvalues.Defaultable;
import org.broadinstitute.dsm.model.defaultvalues.DefaultableMaker;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class RegisterParticipantRoute extends RequestHandler {
    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {

        QueryParamsMap queryParamsMap = request.queryMap();

        String participantId = queryParamsMap.get(RoutePath.DDP_PARTICIPANT_ID).value();
        if (StringUtils.isBlank(participantId)) {
            throw new IllegalArgumentException("participant ID cannot be empty");
        }

        String realm = queryParamsMap.get(RoutePath.REALM).value();
        if (StringUtils.isBlank(realm)) {
            throw new IllegalArgumentException("realm cannot be empty");
        }

        // TODO: check user access

        if (!ParticipantUtil.isGuid(participantId)) {
            throw new IllegalArgumentException("invalid participant ID");
        }

        try {
            Study study = Study.of(realm.toUpperCase());
            Defaultable defaultable = DefaultableMaker.makeDefaultable(study);
            boolean result = defaultable.generateDefaults(realm, participantId);
            if (!result) {
                // TODO: fix message and exception type
                throw new IllegalArgumentException("invalid participant ID");
            }
            return new Result(200);
        } catch (Exception e) {
            // TODO: fix status code
            response.status(500);
            return new Result(500, e.getMessage());
        }
    }
}
