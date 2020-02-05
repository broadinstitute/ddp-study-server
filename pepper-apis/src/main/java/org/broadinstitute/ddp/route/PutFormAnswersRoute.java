package org.broadinstitute.ddp.route;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.FormInstanceDao;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.json.PutAnswersResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.json.workflow.WorkflowResponse;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.instance.validation.ActivityValidationFailure;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.workflow.ActivityState;
import org.broadinstitute.ddp.model.workflow.WorkflowState;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.service.ActivityValidationService;
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
    private final ActivityValidationService actValidationService;
    private final FormInstanceDao formInstanceDao;
    private final PexInterpreter interpreter;

    public PutFormAnswersRoute(
            WorkflowService workflowService,
            ActivityValidationService actValidationService,
            FormInstanceDao formInstanceDao,
            PexInterpreter interpreter
    ) {
        this.workflowService = workflowService;
        this.actValidationService = actValidationService;
        this.formInstanceDao = formInstanceDao;
        this.interpreter = interpreter;
    }

    @Override
    public Object handle(Request request, Response response) {
        String userGuid = request.params(PathParam.USER_GUID);
        String studyGuid = request.params(PathParam.STUDY_GUID);
        String instanceGuid = request.params(PathParam.INSTANCE_GUID);

        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        String operatorGuid = ddpAuth.getOperator() != null ? ddpAuth.getOperator() : userGuid;
        String acceptLanguageHeader = request.headers(RouteConstants.ACCEPT_LANGUAGE);

        LOG.info("Completing form for user {}, operator {}, activity instance {}", userGuid, operatorGuid, instanceGuid);

        PutAnswersResponse resp = TransactionWrapper.withTxn(
                handle -> {
                    RouteUtil.findAccessibleInstanceOrHalt(response, handle, userGuid, studyGuid, instanceGuid);

                    Locale preferredUserLanguage = RouteUtil.resolvePreferredUserLanguage(
                            handle, acceptLanguageHeader, ddpAuth.getPreferredLocale(), studyGuid
                    );
                    String isoLangCode = preferredUserLanguage.getLanguage();
                    long langCodeId = handle.attach(JdbiLanguageCode.class).getLanguageCodeId(isoLangCode);

                    FormInstance form = formInstanceDao.getBaseFormByGuid(handle, instanceGuid, isoLangCode);
                    if (form == null) {
                        String msg = String.format("Could not find activity instance %s for user %s using language %s",
                                instanceGuid, userGuid, isoLangCode);
                        LOG.warn(msg);
                        throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.ACTIVITY_NOT_FOUND, msg));
                    }

                    if (form.isReadonly()) {
                        String msg = "Activity instance " + instanceGuid + " is read-only, cannot update activity";
                        LOG.info(msg);
                        throw ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.ACTIVITY_INSTANCE_IS_READONLY, msg));
                    }

                    formInstanceDao.loadAllSectionsForForm(handle, form, langCodeId);
                    form.updateBlockStatuses(handle, interpreter, userGuid, instanceGuid);

                    if (!form.isComplete()) {
                        String msg = "The status cannot be set to COMPLETE because the question requirements are not met";
                        LOG.info(msg);
                        throw ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.QUESTION_REQUIREMENTS_NOT_MET, msg));
                    }

                    List<ActivityValidationFailure> validationFailures = actValidationService.validate(
                            handle, interpreter, userGuid, instanceGuid, form.getActivityId(), langCodeId
                    );
                    if (!validationFailures.isEmpty()) {
                        String msg = "Activity validation failed";
                        List<String> validationErrorSummaries = validationFailures
                                .stream().map(failure -> failure.getErrorMessage()).collect(Collectors.toList());
                        LOG.info(msg + ", reasons: {}", validationErrorSummaries);
                        throw ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.ACTIVITY_VALIDATION, msg));
                    }

                    FormActivityStatusUtil.updateFormActivityStatus(
                            handle, InstanceStatusType.COMPLETE, instanceGuid, operatorGuid
                    );

                    WorkflowState fromState = new ActivityState(form.getActivityId());
                    WorkflowResponse workflowResp = workflowService
                            .suggestNextState(handle, operatorGuid, userGuid, studyGuid, fromState)
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
