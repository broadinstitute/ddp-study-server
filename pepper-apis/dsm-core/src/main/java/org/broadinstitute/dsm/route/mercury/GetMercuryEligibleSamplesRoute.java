package org.broadinstitute.dsm.route.mercury;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dao.kit.KitDaoImpl;
import org.broadinstitute.dsm.db.dao.mercury.MercurySampleDao;
import org.broadinstitute.dsm.db.dto.mercury.MercurySampleDto;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

@Slf4j
public class GetMercuryEligibleSamplesRoute extends RequestHandler {
    private MercurySampleDao mercurySampleDao;
    private KitDaoImpl kitDaoImpl;
    public static String projectId;
    public static String topicId;


    public GetMercuryEligibleSamplesRoute(MercurySampleDao mercurySampleDao, String projectId, String topicId,
                                          KitDaoImpl kitDao) {
        this.mercurySampleDao = mercurySampleDao;
        this.projectId = projectId;
        this.topicId = topicId;
        this.kitDaoImpl = kitDao;
    }

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();
        if (queryParams.value(RoutePath.REALM) == null) {
            throw new RuntimeException("No realm query param was sent");
        }
        String realm = queryParams.get(RoutePath.REALM).value();
        String userIdRequest = UserUtil.getUserId(request);
        if (!UserUtil.checkUserAccess(realm, userId, "kit_sequencing_order", userIdRequest)) {
            log.warn("User doesn't have access " + userId);
            response.status(500);
            return UserErrorMessages.NO_RIGHTS;
        }
        if (queryParams.value(RoutePath.DDP_PARTICIPANT_ID) == null) {
            throw new RuntimeException("No ddpParticipantId query param was sent");
        }
        String ddpParticipantId = queryParams.get(RoutePath.DDP_PARTICIPANT_ID).value();
        List<MercurySampleDto> eligibleSamples = mercurySampleDao.findEligibleSamples(ddpParticipantId, realm, kitDaoImpl);
        log.info(String.format("Returning a list of %d samples", eligibleSamples.size()));
        return eligibleSamples;
    }

}
