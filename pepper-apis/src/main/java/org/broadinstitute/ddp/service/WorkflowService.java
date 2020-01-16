package org.broadinstitute.ddp.service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.WorkflowDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.json.workflow.WorkflowResponse;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.workflow.ActivityState;
import org.broadinstitute.ddp.model.workflow.NextStateCandidate;
import org.broadinstitute.ddp.model.workflow.StateType;
import org.broadinstitute.ddp.model.workflow.StaticState;
import org.broadinstitute.ddp.model.workflow.WorkflowState;
import org.broadinstitute.ddp.pex.PexException;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkflowService {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowService.class);

    private final PexInterpreter interpreter;

    public WorkflowService(PexInterpreter interpreter) {
        this.interpreter = interpreter;
    }

    public Optional<WorkflowState> suggestNextState(
            Handle handle,
            String operatorGuid,
            String userGuid,
            String studyGuid,
            WorkflowState fromState
    ) {
        long studyId = handle.attach(JdbiUmbrellaStudy.class)
                .getIdByGuid(studyGuid)
                .orElseThrow(() -> new NoSuchElementException("Cannot find study " + studyGuid));
        Optional<Long> fromStateId = handle.attach(WorkflowDao.class).findWorkflowStateId(fromState);
        if (!fromStateId.isPresent()) {
            LOG.info("No id found for fromState {}, likely no transitions are configured with it; returning no suggestions", fromState);
            return Optional.empty();
        }

        List<NextStateCandidate> candidates = handle.attach(WorkflowDao.class)
                .findOrderedActiveNextStateCandidates(studyId, fromStateId.get());
        LOG.info("Found {} active transitions for study {} and fromState {}", candidates.size(), studyGuid, fromState);

        WorkflowState next = null;
        for (NextStateCandidate candidate : candidates) {
            boolean success = true;
            if (candidate.hasPrecondition()) {
                try {
                    success = interpreter.eval(candidate.getPrecondition(), handle, userGuid, null);
                } catch (PexException e) {
                    LOG.warn("Error evaluating pex expression: `{}`", candidate.getPrecondition(), e);
                    success = false;
                }
            }
            if (success) {
                next = candidate.asWorkflowState();
                break;
            }
        }

        Optional<WorkflowState> nextState = Optional.ofNullable(next);
        nextState.ifPresent(nextWfState -> {
            createActivityInstanceIfMissing(handle, fromState, nextWfState, operatorGuid, userGuid);
        });

        return nextState;
    }

    public WorkflowResponse buildStateResponse(Handle handle, String userGuid, WorkflowState state) {
        if (state != null) {
            if (state.getType() == StateType.ACTIVITY) {
                long activityId = ((ActivityState) state).getActivityId();
                return handle.attach(WorkflowDao.class)
                        .findActivityCodeAndLatestInstanceGuidAsResponse(activityId, userGuid)
                        .orElseThrow(() -> new NoSuchElementException("Could not find activity data to build response for " + state));
            } else {
                return WorkflowResponse.from((StaticState) state);
            }
        }
        return WorkflowResponse.unknown();
    }

    private void createActivityInstanceIfMissing(
            Handle handle,
            WorkflowState fromState,
            WorkflowState nextState,
            String operatorGuid,
            String userGuid
    ) {
        if (nextState.getType() != StateType.ACTIVITY) {
            return;
        }
        ActivityState activityState = (ActivityState) nextState;
        String instanceGuid = handle.attach(JdbiActivityInstance.class)
                .findLatestInstanceGuidByUserGuidAndActivityId(userGuid, activityState.getActivityId())
                .orElse(null);
        boolean activityInstanceIsMissing = instanceGuid == null;
        if (activityInstanceIsMissing) {
            ActivityInstanceDto instanceDto = handle.attach(ActivityInstanceDao.class)
                    .insertInstance(activityState.getActivityId(), operatorGuid, userGuid, InstanceStatusType.CREATED, false);
            LOG.info("Created start activity instance with guid '{}' for user guid '{}' using operator guid '{}' and activity id {}",
                    instanceDto.getGuid(), userGuid, operatorGuid, activityState.getActivityId());
        } else {
            LOG.info("User guid '{}' already has activity instance with guid '{}', nothing to create", userGuid, instanceGuid);
        }
    }
}
