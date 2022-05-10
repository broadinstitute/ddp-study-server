package org.broadinstitute.ddp.route;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.json.PatchSectionPayload;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import spark.Request;
import spark.Response;

@Slf4j
@AllArgsConstructor
public class PatchActivityInstanceRoute extends ValidatedJsonInputRoute<PatchSectionPayload> {
    private final org.broadinstitute.ddp.db.ActivityInstanceDao activityInstanceDao;

    @Override
    public Object handle(Request request, Response response, PatchSectionPayload payload) throws Exception {
        String userGuid = request.params(RouteConstants.PathParam.USER_GUID);
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String instanceGuid = request.params(RouteConstants.PathParam.INSTANCE_GUID);

        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        String operatorGuid = StringUtils.defaultIfBlank(ddpAuth.getOperator(), userGuid);
        boolean isStudyAdmin = ddpAuth.hasAdminAccessToStudy(studyGuid);

        log.info("Request to update section index on instance {} for participant {} in study {} by operator {} (isStudyAdmin={})",
                instanceGuid, userGuid, studyGuid, operatorGuid, isStudyAdmin);

        TransactionWrapper.useTxn(handle -> {
            RouteUtil.findAccessibleInstanceOrHalt(response, handle, userGuid, studyGuid, instanceGuid, isStudyAdmin);
            int sectionsSize = activityInstanceDao.getActivityInstanceSectionsSize(handle, userGuid, studyGuid, instanceGuid);
            int index = payload.getIndex();

            if (index != 0 && sectionsSize <= index) {
                String msg = String.format("Activity %s has sections size %s less than index %s", instanceGuid, sectionsSize, index);
                log.error(msg);
                throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, new ApiError(ErrorCodes.BAD_PAYLOAD, msg));
            }

            handle.attach(ActivityInstanceDao.class).updateSectionIndexByInstanceGuid(instanceGuid, index);
            log.info("Updated section index on instance {} for participant {} in study {}", instanceGuid, userGuid, studyGuid);
        });
        response.status(HttpStatus.SC_OK);
        return "";
    }

    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_BAD_REQUEST;
    }
}
