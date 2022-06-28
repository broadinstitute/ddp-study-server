package org.broadinstitute.ddp.route;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.json.ActivityInstanceDeletionResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.service.ActivityInstanceService;
import org.broadinstitute.ddp.util.ActivityInstanceUtil;
import org.broadinstitute.ddp.util.QuestionUtil;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import spark.Request;
import spark.Response;
import spark.Route;

@Slf4j
@AllArgsConstructor
public class DeleteActivityInstanceRoute implements Route {
    private final ActivityInstanceService service;

    @Override
    public Object handle(Request request, Response response) {
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String participantGuid = request.params(RouteConstants.PathParam.USER_GUID);
        String instanceGuid = request.params(RouteConstants.PathParam.INSTANCE_GUID);

        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        String operatorGuid = StringUtils.defaultIfBlank(ddpAuth.getOperator(), participantGuid);
        boolean isStudyAdmin = ddpAuth.hasAdminAccessToStudy(studyGuid);

        log.info("Attempting to delete activity instance {} for participant {} in study {} by operator {} (isStudyAdmin={})",
                instanceGuid, participantGuid, studyGuid, operatorGuid, isStudyAdmin);

        ActivityInstanceDeletionResponse res = new ActivityInstanceDeletionResponse();

        TransactionWrapper.useTxn(handle -> {
            var found = RouteUtil.findUserAndStudyOrHalt(handle, participantGuid, studyGuid);
            ActivityInstanceDto instanceDto = RouteUtil.findAccessibleInstanceOrHalt(
                    response, handle, found.getUser(), found.getStudyDto(), instanceGuid, isStudyAdmin);

            ActivityDefStore activityStore = ActivityDefStore.getInstance();
            FormActivityDef activityDef = ActivityInstanceUtil.getActivityDef(handle, activityStore, instanceDto, studyGuid);

            Long previousInstanceId = ActivityInstanceUtil.getPreviousInstanceId(handle, instanceDto.getId());
            boolean isFirstInstance = previousInstanceId == null;
            boolean canDelete = ActivityInstanceUtil.computeCanDelete(
                    activityDef.canDeleteInstances(),
                    activityDef.getCanDeleteFirstInstance(),
                    isFirstInstance);

            if (!canDelete && !activityDef.canDeleteInstances()) {
                throwNotAllowed(response, "Activity does not allow deleting instances");
            } else if (!canDelete && isFirstInstance) {
                throwNotAllowed(response, "Activity does not allow deleting the first instance");
            } else if (!canDelete) {
                throwNotAllowed(response, "Activity instance is not allowed to be deleted");
            } else if (instanceDto.getParentActivityCode() == null) {
                throwNotAllowed(response, "Deleting non-nested activity instances is not supported");
            }

            service.deleteInstance(handle, instanceDto);
            handle.attach(DataExportDao.class).queueDataSync(instanceDto.getParticipantId(), instanceDto.getStudyId());

            String parentInstanceGuid = instanceDto.getParentInstanceGuid();
            res.setBlockVisibilities(QuestionUtil.getBlockVisibility(handle, response, parentInstanceGuid,
                    found.getUser(), found.getStudyDto(), operatorGuid, isStudyAdmin));
        });

        log.info("Deleted activity instance {}", instanceGuid);
        response.status(HttpStatus.SC_OK);
        return res;
    }

    private void throwNotAllowed(Response response, String message) {
        log.warn(message);
        throw ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY,
                new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED, message));
    }

}
