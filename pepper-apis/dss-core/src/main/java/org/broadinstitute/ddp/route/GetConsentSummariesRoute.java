package org.broadinstitute.ddp.route;

import java.util.List;

import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.json.consent.ConsentSummary;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.service.ConsentService;
import org.broadinstitute.ddp.util.RouteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * Given the study and the user GUID, returns all instances of all consents
 */
public class GetConsentSummariesRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(GetConsentSummariesRoute.class);

    private final ConsentService service;

    public GetConsentSummariesRoute(ConsentService service) {
        this.service = service;
    }

    @Override
    public Object handle(Request request, Response response) {
        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        String operatorGuid = ddpAuth.getOperator();
        String userGuid = request.params(PathParam.USER_GUID);
        String studyGuid = request.params(PathParam.STUDY_GUID);

        LOG.info("Attempting to retrieve all consent summaries for participant {} in study {}", userGuid, studyGuid);

        List<ConsentSummary> summaries = TransactionWrapper.withTxn(
                handle -> service.getAllConsentSummariesByUserGuid(handle, userGuid, operatorGuid, studyGuid));

        LOG.info("Retrieved {} consent summaries for participant {} in study {}",
                summaries.size(), userGuid, studyGuid);
        return summaries;
    }
}
