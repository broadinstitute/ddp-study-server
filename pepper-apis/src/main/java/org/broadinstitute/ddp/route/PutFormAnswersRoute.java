package org.broadinstitute.ddp.route;

import java.util.Optional;

import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.db.FormInstanceDao;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.json.PutAnswersResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.json.workflow.WorkflowResponse;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.workflow.ActivityState;
import org.broadinstitute.ddp.model.workflow.WorkflowState;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.service.WorkflowService;
import org.broadinstitute.ddp.util.FormActivityStatusUtil;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class PutFormAnswersRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(PutFormAnswersRoute.class);

    private final WorkflowService workflowService;
    private final FormInstanceDao formInstanceDao;
    private final PexInterpreter interpreter;

    public PutFormAnswersRoute(WorkflowService workflowService, FormInstanceDao formInstanceDao, PexInterpreter interpreter) {
        this.workflowService = workflowService;
        this.formInstanceDao = formInstanceDao;
        this.interpreter = interpreter;
    }

    @Override
    public Object handle(Request request, Response response) {
        String userGuid = request.params(PathParam.USER_GUID);
        String studyGuid = request.params(PathParam.STUDY_GUID);
        String activityInstanceGuid = request.params(PathParam.INSTANCE_GUID);

        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        String operatorGuid = ddpAuth.getOperator() != null ? ddpAuth.getOperator() : userGuid;

        LOG.info("Completing form for user {}, operator {}, activity instance {}",
                userGuid, operatorGuid, activityInstanceGuid);

        PutAnswersResponse resp = TransactionWrapper.withTxn(
                handle -> {
                    UserDto userDto = handle.attach(JdbiUser.class).findByUserGuid(userGuid);
                    if (userDto.isTemporary()) {
                        Optional<ActivityInstanceDto> activityInstanceDto =
                                handle.attach(JdbiActivityInstance.class).getByActivityInstanceGuid(activityInstanceGuid);
                        if (!activityInstanceDto.isPresent()) {
                            String msg = "Activity instance " + activityInstanceGuid + " is not found";
                            ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.ACTIVITY_NOT_FOUND, msg));
                        } else if (!activityInstanceDto.get().isAllowUnauthenticated()) {
                            String msg = "Activity instance " + activityInstanceGuid + " not accessible to unauthenticated users";
                            ResponseUtil.haltError(response, 401, new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED, msg));
                        }
                    }

                    String isoLangCode = ddpAuth.getPreferredLanguage();
                    if (isoLangCode == null) {
                        isoLangCode = I18nContentRenderer.DEFAULT_LANGUAGE_CODE;
                    }
                    long langCodeId = handle.attach(JdbiLanguageCode.class).getLanguageCodeId(isoLangCode);

                    FormInstance form = formInstanceDao.getBaseFormByGuid(handle, activityInstanceGuid, isoLangCode);
                    if (form == null) {
                        String msg = String.format("Could not find activity instance %s for user %s using language %s",
                                activityInstanceGuid, userGuid, isoLangCode);
                        LOG.warn(msg);
                        ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.ACTIVITY_NOT_FOUND, msg));
                        return null;
                    }

                    if (form.isReadonly()) {
                        String msg = "Activity instance " + activityInstanceGuid + " is read-only, cannot update activity";
                        LOG.info(msg);
                        ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.ACTIVITY_INSTANCE_IS_READONLY, msg));
                        return null;
                    }

                    formInstanceDao.loadAllSectionsForForm(handle, form, langCodeId);
                    form.updateBlockStatuses(handle, interpreter, userGuid, activityInstanceGuid);

                    if (!form.isComplete()) {
                        String msg = "The status cannot be set to COMPLETE because the question requirements are not met";
                        LOG.info(msg);
                        ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.QUESTION_REQUIREMENTS_NOT_MET, msg));
                        return null;
                    }

                    FormActivityStatusUtil.updateFormActivityStatus(
                            handle, InstanceStatusType.COMPLETE, activityInstanceGuid, operatorGuid
                    );

                    WorkflowState fromState = new ActivityState(form.getActivityId());
                    WorkflowResponse workflowResp = workflowService
                            .suggestNextState(handle, userGuid, studyGuid, fromState)
                            .map(nextState -> {
                                LOG.info("Suggesting user {} to next state {}", userGuid, nextState);
                                return workflowService.buildStateResponse(handle, userGuid, nextState);
                            })
                            .orElse(WorkflowResponse.unknown());

                    handle.attach(DataExportDao.class).queueDataSync(userGuid, studyGuid);
                    return new PutAnswersResponse(workflowResp);
                }
        );

        response.status(200);
        return resp;
    }
}
