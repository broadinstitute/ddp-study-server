package org.broadinstitute.ddp.route;

import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.json.consent.ConsentSummary;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.service.ConsentService;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * Given the study, the user and the consent activity code
 * returns the latest consent instance for this activity
 */
public class GetConsentSummaryRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(GetConsentSummaryRoute.class);

    private final ConsentService service;

    public GetConsentSummaryRoute(ConsentService service) {
        this.service = service;
    }

    @Override
    public Object handle(Request request, Response response) {
        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        String operatorGuid = ddpAuth.getOperator();
        String userGuid = request.params(PathParam.USER_GUID);
        String studyGuid = request.params(PathParam.STUDY_GUID);
        String consentActivityCode = request.params(PathParam.ACTIVITY_CODE);

        LOG.info("Attempting to retrieve consent summary {} for participant {} in study {}",
                consentActivityCode, userGuid, studyGuid);

        ConsentSummary summary = TransactionWrapper.withTxn(
                handle -> service.getLatestConsentSummary(handle, userGuid, operatorGuid, studyGuid, consentActivityCode).orElse(null));

        if (summary == null) {
            String msg = "Study does not have consent activity";
            ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.ACTIVITY_NOT_FOUND, msg));
        }

        LOG.info("Retrieved consent summary {} for participant {} in study {}",
                summary.getActivityCode(), userGuid, studyGuid);
        return summary;
    }
}
