package org.broadinstitute.dsm.route.mercury;

import java.util.ArrayList;
import java.util.Collection;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dao.mercury.ClinicalOrderDao;
import org.broadinstitute.dsm.db.dao.mercury.MercuryOrderDao;
import org.broadinstitute.dsm.db.dao.mercury.MercurySampleDao;
import org.broadinstitute.dsm.db.dto.mercury.ClinicalOrderDto;
import org.broadinstitute.dsm.db.dto.mercury.ClinicalOrderUseCase;
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
    public static String projectId;
    public static String topicId;
    private ClinicalOrderUseCase clinicalOrderUseCase = new ClinicalOrderUseCase();
    private ClinicalOrderDao clinicalOrderDao;
    private MercuryOrderDao mercuryOrderDao;

    public GetMercuryEligibleSamplesRoute(MercurySampleDao mercurySampleDao, MercuryOrderDao mercuryOrderDao,
                                          ClinicalOrderDao clinicalOrderDao, String projectId, String topicId) {
        this.mercurySampleDao = mercurySampleDao;
        this.mercuryOrderDao = mercuryOrderDao;
        this.clinicalOrderDao = clinicalOrderDao;
        this.projectId = projectId;
        this.topicId = topicId;
    }

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();
        if (queryParams.value(RoutePath.REALM) == null) {
            throw new RuntimeException("No realm query param was sent");
        }
        String realm = queryParams.get(RoutePath.REALM).value();
        if (!UserUtil.checkUserAccess(realm, userId, "mercury_order_sequencing", null)) {
            log.warn("User doesn't have access");
            response.status(500);
            return new Result(500, UserErrorMessages.NO_RIGHTS);
        }
        if (request.url().contains(RoutePath.MERCURY_SAMPLES_ROUTE)) {
            if (queryParams.value(RoutePath.DDP_PARTICIPANT_ID) == null) {
                throw new RuntimeException("No ddpParticipantId query param was sent");
            }
            String ddpParticipantId = queryParams.get(RoutePath.DDP_PARTICIPANT_ID).value();
            ArrayList<MercurySampleDto> eligibleSamples = mercurySampleDao.findEligibleSamples(ddpParticipantId, realm);
            log.info(String.format("Returning a list of %d samples", eligibleSamples.size()));
            return eligibleSamples;
        }
        if (request.url().contains(RoutePath.GET_MERCURY_ORDERS_ROUTE)) {
            Collection<ClinicalOrderDto> orders = clinicalOrderDao.getOrdersForRealm(realm, projectId, topicId, clinicalOrderUseCase);
            log.info(String.format("Returning a list of %d orders", orders.size()));
            return orders;
        }
        return null;
    }

}
