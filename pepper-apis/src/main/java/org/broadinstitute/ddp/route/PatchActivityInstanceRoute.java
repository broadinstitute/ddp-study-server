package org.broadinstitute.ddp.route;

import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.json.PatchSectionPayload;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;


public class PatchActivityInstanceRoute extends ValidatedJsonInputRoute<PatchSectionPayload> {

    private static final Logger LOG = LoggerFactory.getLogger(PatchActivityInstanceRoute.class);

    private org.broadinstitute.ddp.db.ActivityInstanceDao activityInstanceDao;

    public PatchActivityInstanceRoute(org.broadinstitute.ddp.db.ActivityInstanceDao activityInstanceDao) {
        this.activityInstanceDao = activityInstanceDao;
    }

    @Override
    public Object handle(Request request, Response response, PatchSectionPayload payload) throws Exception {
        String userGuid = request.params(RouteConstants.PathParam.USER_GUID);
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String instanceGuid = request.params(RouteConstants.PathParam.INSTANCE_GUID);

        LOG.info("Request to update section index on instance {} for participant {} in study {}",
                instanceGuid, userGuid, studyGuid);

        TransactionWrapper.useTxn(handle -> {
            RouteUtil.findAccessibleInstanceOrHalt(response, handle, userGuid, studyGuid, instanceGuid);
            int sectionsSize = activityInstanceDao.getActivityInstanceSectionsSize(handle, userGuid, studyGuid, instanceGuid);
            int index = payload.getIndex();

            if (sectionsSize < index) {
                String msg = String.format("Activity %s has sections size %s less than index %s", instanceGuid, sectionsSize, index);
                LOG.error(msg);
                throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, new ApiError(ErrorCodes.INVALID_REQUEST, msg));
            }

            handle.attach(ActivityInstanceDao.class).updateSectionIndexByInstanceGuid(instanceGuid, index);
        });
        response.status(HttpStatus.SC_OK);
        return "";
    }

    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_BAD_REQUEST;
    }

}
