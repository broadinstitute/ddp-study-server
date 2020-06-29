package org.broadinstitute.ddp.route;

import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.json.PatchLastVisitedSectionPayload;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;


public class PatchLastVisitedSectionRoute extends ValidatedJsonInputRoute<PatchLastVisitedSectionPayload> {

    private static final Logger LOG = LoggerFactory.getLogger(PatchLastVisitedSectionRoute.class);

    public PatchLastVisitedSectionRoute() {
    }

    @Override
    public Object handle(Request request, Response response, PatchLastVisitedSectionPayload dataObject) throws Exception {
        String userGuid = request.params(RouteConstants.PathParam.USER_GUID);
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String instanceGuid = request.params(RouteConstants.PathParam.INSTANCE_GUID);

        LOG.info("Request to update last visited section on instance {} for participant {} in study {}",
                instanceGuid, userGuid, studyGuid);

        TransactionWrapper.useTxn(handle -> {
            int lastVisitedSection = dataObject.getLastVisitedSection();

            handle.attach(ActivityInstanceDao.class)
                    .updateLastVisitedSectionByInstanceGuid(instanceGuid, lastVisitedSection);
        });
        response.status(HttpStatus.SC_OK);
        return "";
    }

    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_BAD_REQUEST;
    }

}
