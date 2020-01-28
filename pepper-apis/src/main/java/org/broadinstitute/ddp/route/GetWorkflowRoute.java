package org.broadinstitute.ddp.route;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.constants.RouteConstants.QueryParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.json.workflow.WorkflowResponse;
import org.broadinstitute.ddp.model.workflow.ActivityState;
import org.broadinstitute.ddp.model.workflow.StateType;
import org.broadinstitute.ddp.model.workflow.StaticState;
import org.broadinstitute.ddp.model.workflow.WorkflowState;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.service.WorkflowService;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * Given the current context of the user, returns the next state for the front-end. For example,
 * the route can indicate that the user should be shown the "Thank you" page or redirected to the Dashboard
 */
public class GetWorkflowRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(GetWorkflowRoute.class);

    private final WorkflowService workflowService;

    public GetWorkflowRoute(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Override
    public WorkflowResponse handle(Request request, Response response) {
        String userGuid = request.params(PathParam.USER_GUID);
        String studyGuid = request.params(PathParam.STUDY_GUID);
        String fromParam = request.queryParams(QueryParam.FROM);
        String activityCodeParam = request.queryParams(QueryParam.ACTIVITY_CODE);
        String instanceGuidParam = request.queryParams(QueryParam.INSTANCE_GUID);

        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        String operatorGuid = ddpAuth.getOperator() != null ? ddpAuth.getOperator() : userGuid;

        LOG.info("Attempting to find workflow suggestion for user {} and study {} using 'from' param {}, activity code '{}',"
                + " instance guid '{}'", userGuid, studyGuid, fromParam, activityCodeParam, instanceGuidParam);

        StateType fromType = convertParamToType(response, fromParam);

        return TransactionWrapper.withTxn(handle -> {
            Long studyId = handle.attach(JdbiUmbrellaStudy.class)
                    .getIdByGuid(studyGuid)
                    .orElseGet(() -> {
                        String msg = String.format("The study guid '%s' does not refer to a valid study", studyGuid);
                        LOG.warn(msg);
                        throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.STUDY_NOT_FOUND, msg));
                    });

            WorkflowState fromState = convertTypeToState(handle, response, userGuid,
                    fromType, activityCodeParam, instanceGuidParam, studyId);
            LOG.info("Finding suggestions using 'from' state {}", fromState);

            return workflowService
                    .suggestNextState(handle, operatorGuid, userGuid, studyGuid, fromState)
                    .map(nextState -> {
                        LOG.info("Returning next state suggestion {}", nextState);
                        return workflowService.buildStateResponse(handle, userGuid, nextState);
                    })
                    .orElseGet(() -> {
                        LOG.info("No next state suggestions found");
                        return WorkflowResponse.unknown();
                    });
        });
    }

    private void haltWithMissingFromState(Response response, String msg) {
        LOG.warn(msg);
        ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.MISSING_FROM_PARAM, msg));
    }

    private void haltWithInvalidFromState(Response response, String msg) {
        LOG.warn(msg);
        ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.INVALID_FROM_PARAM, msg));
    }

    private StateType convertParamToType(Response response, String fromParam) {
        if (StringUtils.isBlank(fromParam)) {
            String msg = "Query parameter for 'from' state is required";
            haltWithMissingFromState(response, msg);
            return null;
        }
        try {
            return StateType.valueOf(fromParam);
        } catch (IllegalArgumentException e) {
            String msg = String.format("Query parameter value '%s' for 'from' state is not a valid state", fromParam);
            haltWithInvalidFromState(response, msg);
            return null;
        }
    }

    private WorkflowState convertTypeToState(Handle handle, Response response, String userGuid, StateType fromType,
                                             String activityCodeParam, String instanceGuidParam, long studyId) {
        WorkflowState fromState;
        if (fromType.isStatic()) {
            fromState = StaticState.of(fromType);
        } else if (fromType == StateType.ACTIVITY) {
            fromState = buildActivityState(handle, response, userGuid, activityCodeParam, instanceGuidParam, studyId);
        } else {
            String msg = String.format("'from' state %s is not recognized", fromType);
            haltWithInvalidFromState(response, msg);
            return null;
        }
        return fromState;
    }

    private ActivityState buildActivityState(Handle handle, Response response, String userGuid,
                                             String activityCodeParam, String instanceGuidParam, long studyId) {
        long activityId;
        if (StringUtils.isNotBlank(activityCodeParam)) {
            activityId = lookupActivityIdByActivityCode(handle, response, activityCodeParam, studyId);
        } else if (StringUtils.isNotBlank(instanceGuidParam)) {
            activityId = lookupActivityIdByInstance(handle, response, userGuid, instanceGuidParam);
        } else {
            String msg = "Activity 'from' state requires activity code or instance guid";
            haltWithInvalidFromState(response, msg);
            return null;
        }
        return new ActivityState(activityId);
    }

    private Long lookupActivityIdByActivityCode(Handle handle, Response response, String activityCode, long studyId) {
        return handle.attach(JdbiActivity.class)
                .findIdByStudyIdAndCode(studyId, activityCode)
                .orElseGet(() -> {
                    String msg = String.format("The activity code '%s' for 'from' state"
                            + " does not refer to a valid activity", activityCode);
                    haltWithInvalidFromState(response, msg);
                    return null;
                });
    }

    private Long lookupActivityIdByInstance(Handle handle, Response response, String userGuid, String instanceGuid) {
        return handle.attach(JdbiActivityInstance.class)
                .getByUserAndInstanceGuids(userGuid, instanceGuid)
                .map(ActivityInstanceDto::getActivityId)
                .orElseGet(() -> {
                    String msg = String.format("The instance guid '%s' for 'from' state"
                            + " does not refer to a valid activity", instanceGuid);
                    haltWithInvalidFromState(response, msg);
                    return null;
                });
    }
}
