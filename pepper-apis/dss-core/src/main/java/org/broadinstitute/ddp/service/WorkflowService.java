package org.broadinstitute.ddp.service;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudyCached;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudyI18n;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dao.WorkflowDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.StudyI18nDto;
import org.broadinstitute.ddp.json.workflow.WorkflowActivityResponse;
import org.broadinstitute.ddp.json.workflow.WorkflowResponse;
import org.broadinstitute.ddp.json.workflow.WorkflowStudyRedirectResponse;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.event.ActivityInstanceStatusChangeSignal;
import org.broadinstitute.ddp.model.event.EventSignal;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.model.workflow.ActivityState;
import org.broadinstitute.ddp.model.workflow.NextStateCandidate;
import org.broadinstitute.ddp.model.workflow.StateType;
import org.broadinstitute.ddp.model.workflow.StaticState;
import org.broadinstitute.ddp.model.workflow.StudyRedirectState;
import org.broadinstitute.ddp.model.workflow.WorkflowState;
import org.broadinstitute.ddp.pex.PexException;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;

@Slf4j
@AllArgsConstructor
public class WorkflowService {
    private final PexInterpreter interpreter;

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
        if (fromStateId.isEmpty()) {
            log.info("No id found for fromState {}, likely no transitions are configured with it; returning no suggestions", fromState);
            return Optional.empty();
        }

        List<NextStateCandidate> candidates = handle.attach(WorkflowDao.class)
                .findOrderedActiveNextStateCandidates(studyId, fromStateId.get());
        log.info("Found {} active transitions for study {} and fromState {}", candidates.size(), studyGuid, fromState);

        WorkflowState next = null;
        for (NextStateCandidate candidate : candidates) {
            if (candidate.getNextStateType() == StateType.ACTIVITY && candidate.shouldCheckEachInstance()) {
                List<ActivityInstanceDto> instanceDtos = handle.attach(JdbiActivityInstance.class)
                        .findAllByUserGuidAndActivityId(userGuid, candidate.getNextActivityId())
                        .stream()
                        .sorted(Comparator.comparing(ActivityInstanceDto::getCreatedAtMillis))
                        .collect(Collectors.toList());
                ActivityState found = null;
                for (var instanceDto : instanceDtos) {
                    if (passedCondition(handle, candidate, userGuid, operatorGuid, instanceDto.getGuid())) {
                        found = (ActivityState) candidate.asWorkflowState();
                        found.setCandidateInstance(instanceDto);
                        break;
                    }
                }
                if (found != null) {
                    next = found;
                    break;
                }
            } else {
                if (passedCondition(handle, candidate, userGuid, operatorGuid, null)) {
                    next = candidate.asWorkflowState();
                    break;
                }
            }
        }

        Optional<WorkflowState> nextState = Optional.ofNullable(next);
        nextState.ifPresent(nextWfState ->
                createActivityInstanceIfMissing(handle, fromState, nextWfState, operatorGuid, userGuid, studyId, studyGuid));

        return nextState;
    }

    private boolean passedCondition(Handle handle, NextStateCandidate candidate,
                                    String userGuid, String operatorGuid, String instanceGuid) {
        if (candidate.hasPrecondition()) {
            try {
                return interpreter.eval(candidate.getPrecondition(), handle, userGuid, operatorGuid, instanceGuid);
            } catch (PexException e) {
                log.warn("Error evaluating pex expression: `{}`", candidate.getPrecondition(), e);
                return false;
            }
        } else {
            return true;
        }
    }

    public WorkflowResponse buildStateResponse(Handle handle, String userGuid, WorkflowState state) {
        if (state != null) {
            if (state.getType() == StateType.ACTIVITY) {
                ActivityState activityState = (ActivityState) state;
                long activityId = activityState.getActivityId();
                var instanceDto = activityState.getCandidateInstance();
                if (instanceDto != null) {
                    return new WorkflowActivityResponse(
                            instanceDto.getActivityCode(),
                            instanceDto.getGuid(),
                            instanceDto.isAllowUnauthenticated());
                } else {
                    return handle.attach(WorkflowDao.class)
                            .findActivityCodeAndLatestInstanceGuidAsResponse(activityId, userGuid)
                            .orElseThrow(() -> new NoSuchElementException("Could not find activity data to build response for " + state));
                }
            } else if (state.getType() == StateType.STUDY_REDIRECT) {
                StudyRedirectState studyRedirectState = (StudyRedirectState)state;
                String studyName = studyRedirectState.getStudyName();
                if (studyName == null) {
                    if (studyRedirectState.getStudyGuid() != null) {
                        studyName = getStudyName(handle, userGuid, studyRedirectState.getStudyGuid());
                    } else {
                        log.error("Study Name not set for StudyRedirect URL: {} ", studyRedirectState.getRedirectUrl());
                        throw new NoSuchElementException("Could not find studyName for redirect url :"
                                + studyRedirectState.getRedirectUrl());
                    }
                }
                return new WorkflowStudyRedirectResponse(studyName, studyRedirectState.getStudyGuid(), studyRedirectState.getRedirectUrl());
            } else {
                return WorkflowResponse.from((StaticState) state);
            }
        }
        return WorkflowResponse.unknown();
    }

    private String getStudyName(Handle handle, String userGuid, String studyGuid) {
        String studyName;
        JdbiUmbrellaStudy studyDao = new JdbiUmbrellaStudyCached(handle);
        JdbiUmbrellaStudyI18n translationDao = handle.attach(JdbiUmbrellaStudyI18n.class);
        StudyDto study = studyDao.findByStudyGuid(studyGuid);
        if (null == study) {
            throw new NoSuchElementException("Could not find study :" + studyGuid);
        }

        String preferredLanguageCode = null;
        StudyI18nDto preferredTranslation = null;
        Optional<UserProfile> userProfile = handle.attach(UserProfileDao.class).findProfileByUserGuid(userGuid);
        if (userProfile.isPresent()) {
            preferredLanguageCode = userProfile.get().getPreferredLangCode();
        }
        if (preferredLanguageCode != null) {
            List<StudyI18nDto> studyTranslations = translationDao.findTranslationsByStudyId(study.getId());
            for (StudyI18nDto dto : studyTranslations) {
                if (dto.getLanguageCode() != null && dto.getLanguageCode().equals(preferredLanguageCode)) {
                    preferredTranslation = dto;
                    break;
                }
            }
        }
        if (preferredTranslation != null) {
            studyName = preferredTranslation.getName();
        } else {
            studyName = study.getName();
        }
        return studyName;
    }

    private void createActivityInstanceIfMissing(
            Handle handle,
            WorkflowState fromState,
            WorkflowState nextState,
            String operatorGuid,
            String userGuid,
            long studyId,
            String studyGuid
    ) {
        if (nextState.getType() != StateType.ACTIVITY) {
            return;
        }
        ActivityState activityState = (ActivityState) nextState;
        if (activityState.getCandidateInstance() != null) {
            return;
        }
        ActivityDto activityDto = handle.attach(JdbiActivity.class).queryActivityById(activityState.getActivityId());
        if (activityDto.getMaxInstancesPerUser() != null && activityDto.getMaxInstancesPerUser() == 0) {
            return;
        }
        String instanceGuid = handle.attach(JdbiActivityInstance.class)
                .findLatestInstanceGuidByUserGuidAndActivityId(userGuid, activityState.getActivityId())
                .orElse(null);
        boolean activityInstanceIsMissing = instanceGuid == null;
        if (activityInstanceIsMissing) {
            ActivityInstanceDto instanceDto = handle.attach(ActivityInstanceDao.class)
                    .insertInstance(activityState.getActivityId(), operatorGuid, userGuid, InstanceStatusType.CREATED, null);
            log.info("Created activity instance with guid '{}' for user guid '{}' using operator guid '{}' and activity id {}",
                    instanceDto.getGuid(), userGuid, operatorGuid, activityState.getActivityId());
            processActionsForActivityCreationSignal(
                    handle,
                    operatorGuid,
                    instanceDto.getParticipantId(),
                    userGuid,
                    instanceDto.getId(),
                    activityState.getActivityId(),
                    studyId,
                    studyGuid
            );
            log.info(
                    "Processed actions for the activity instance {} of activity {} triggered by the creation signal",
                    instanceDto.getGuid(),
                    activityState.getActivityId()
            );
        } else {
            log.info("User guid '{}' already has activity instance with guid '{}', nothing to create", userGuid, instanceGuid);
        }
    }

    private void processActionsForActivityCreationSignal(
            Handle handle,
            String operatorGuid,
            long participantId,
            String participantGuid,
            long instanceId,
            long activityId,
            long studyId,
            String studyGuid
    ) {
        long operatorId = handle.attach(JdbiUser.class).getUserIdByGuid(operatorGuid);
        EventSignal activityCreationSignal = new ActivityInstanceStatusChangeSignal(
                operatorId,
                participantId,
                participantGuid,
                operatorGuid,
                instanceId,
                activityId,
                studyId,
                studyGuid,
                InstanceStatusType.CREATED
        );
        EventService.getInstance().processSynchronousActionsForEventSignal(handle, activityCreationSignal);
    }
}
