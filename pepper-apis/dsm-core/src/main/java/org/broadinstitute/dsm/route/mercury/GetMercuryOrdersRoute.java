package org.broadinstitute.dsm.route.mercury;

import java.util.ArrayList;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dao.mercury.ClinicalOrderDao;
import org.broadinstitute.dsm.db.dao.mercury.MercurySampleDao;
import org.broadinstitute.dsm.db.dto.mercury.ClinicalOrderDto;
import org.broadinstitute.dsm.db.dto.mercury.ClinicalOrderUseCase;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

@Slf4j
public class GetMercuryOrdersRoute extends RequestHandler {
    private MercurySampleDao mercurySampleDao;
    public static String projectId;
    public static String topicId;
    private ClinicalOrderUseCase clinicalOrderUseCase = new ClinicalOrderUseCase();
    private ClinicalOrderDao clinicalOrderDao;

    public GetMercuryOrdersRoute(MercurySampleDao mercurySampleDao, ClinicalOrderDao clinicalOrderDao, String projectId, String topicId) {
        this.mercurySampleDao = mercurySampleDao;
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
        String userIdRequest = UserUtil.getUserId(request);
        if (!UserUtil.checkUserAccess(realm, userId, "kit_sequencing_order", userIdRequest) &&
                !UserUtil.checkUserAccess(realm, userId, "view_seq_order_status", userIdRequest)) {
            log.warn("User doesn't have access");
            response.status(500);
            return UserErrorMessages.NO_RIGHTS;
        }
        if (request.url().contains(RoutePath.GET_MERCURY_ORDERS_ROUTE)) {
            ArrayList<ClinicalOrderDto> orders = clinicalOrderDao.getOrdersForRealm(realm);
            //            clinicalOrderUseCase.publishStatusActionMessage(orders, projectId, topicId);
            log.info(String.format("Returning a list of %d orders", orders.size()));
            return orders;
        }
        return null;
    }
}
