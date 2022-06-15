package org.broadinstitute.dsm.route.mercury;

import java.util.ArrayList;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dao.mercury.MercurySampleDao;
import org.broadinstitute.dsm.db.dto.mercury.MercurySampleDto;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

@Slf4j
public class GetMercuryEligibleSamplesRoute extends RequestHandler {
    private MercurySampleDao mercurySampleDao;

    public GetMercuryEligibleSamplesRoute(MercurySampleDao mercurySampleDao) {
        this.mercurySampleDao = mercurySampleDao;
    }

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();
        if (queryParams.value(RoutePath.REALM) == null) {
            throw new RuntimeException("No realm query param was sent");
        }
        if (queryParams.value(RoutePath.DDP_PARTICIPANT_ID) == null) {
            throw new RuntimeException("No ddpParticipantId query param was sent");
        }
        String realm = queryParams.get(RoutePath.REALM).value();
        String ddpParticipantId = queryParams.get(RoutePath.DDP_PARTICIPANT_ID).value();
        if (!UserUtil.checkUserAccess(realm, userId, "mr_view", null)) {
            log.warn("User doesn't have access");
            response.status(500);
            return new Result(500, UserErrorMessages.NO_RIGHTS);
        }
        ArrayList<MercurySampleDto> eligibleSamples = mercurySampleDao.findEligibleSamples(ddpParticipantId, realm);
        return eligibleSamples;
    }

}
