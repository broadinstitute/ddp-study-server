package org.broadinstitute.ddp.copy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.copy.CopyAnswerLocation;
import org.broadinstitute.ddp.model.copy.CopyConfiguration;
import org.broadinstitute.ddp.model.copy.CopyLocation;
import org.broadinstitute.ddp.model.copy.CopyLocationType;
import org.broadinstitute.ddp.model.copy.UserType;
import org.jdbi.v3.core.Handle;

@Slf4j
public class CopyExecutor {
    private Long triggeredInstanceId;

    public CopyExecutor withTriggeredInstanceId(long instanceId) {
        this.triggeredInstanceId = instanceId;
        return this;
    }

    public void execute(Handle handle, long operatorId, long participantId, CopyConfiguration config) {
        AnswerToAnswerCopier ansToAnsCopier = new AnswerToAnswerCopier(handle, operatorId);
        AnswerToProfileCopier ansToProfileCopier = new AnswerToProfileCopier(handle, operatorId);

        if (config.shouldCopyFromPreviousInstance()) {
            copyAnswersFromPreviousInstance(handle, config, ansToAnsCopier);
        }

        if (config.getPairs().isEmpty()) {
            return;
        }

        Map<String, QuestionDto> questionDtosByStableId = retrieveQuestionDtos(handle, config);
        Map<Long, FormResponse> responsesById = retrieveActivityData(handle, participantId,
                List.copyOf(questionDtosByStableId.values()));
        UserGovernanceDao governanceDao = handle.attach(UserGovernanceDao.class);
        for (var pair : config.getPairs()) {
            CopyLocation source = pair.getSource();
            CopyLocation target = pair.getTarget();

            if (source.getType() == CopyLocationType.ANSWER && target.getType() == CopyLocationType.ANSWER) {
                QuestionDto sourceQuestion;
                FormResponse sourceInstance;
                String sourceStableId = ((CopyAnswerLocation) source).getQuestionStableId();
                UserType user = ((CopyAnswerLocation) source).getUserType();
                if (user == UserType.OPERATOR) {
                    var governance = governanceDao
                            .findActiveGovernancesByParticipantAndStudyIds(participantId, config.getStudyId())
                            .findFirst()
                            .orElseThrow(() -> new DDPException(String.format("Governance not found for participant %s", participantId)));
                    Map<Long, FormResponse> proxyActivities = retrieveActivityData(handle, governance.getProxyUserId(),
                            List.copyOf(questionDtosByStableId.values()));
                    sourceQuestion = questionDtosByStableId.get(sourceStableId);
                    if (sourceQuestion == null) {
                        continue; // Question might have been removed from activity, so we skip it.
                    }
                    sourceInstance = proxyActivities.get(sourceQuestion.getActivityId());
                } else {
                    sourceQuestion = questionDtosByStableId.get(sourceStableId);
                    if (sourceQuestion == null) {
                        continue; // Question might have been removed from activity, so we skip it.
                    }
                    sourceInstance = responsesById.get(sourceQuestion.getActivityId());
                }
                String targetStableId = ((CopyAnswerLocation) target).getQuestionStableId();
                QuestionDto targetQuestion = questionDtosByStableId.get(targetStableId);
                FormResponse targetInstance = responsesById.get(targetQuestion.getActivityId());

                ansToAnsCopier.copy(sourceInstance, sourceQuestion, targetInstance, targetQuestion);
            } else if (source.getType() == CopyLocationType.ANSWER) {
                String sourceStableId = ((CopyAnswerLocation) source).getQuestionStableId();
                QuestionDto sourceQuestion = questionDtosByStableId.get(sourceStableId);
                if (sourceQuestion == null) {
                    continue;
                }
                FormResponse sourceInstance = responsesById.get(sourceQuestion.getActivityId());

                ansToProfileCopier.copy(sourceInstance, sourceQuestion, target.getType());
            } else {
                throw new DDPException(String.format(
                        "Copying from locations (%s to %s) in copy configuration %d is not supported",
                        source.getType(), target.getType(), config.getId()));
            }
        }
    }

    private void copyAnswersFromPreviousInstance(Handle handle, CopyConfiguration config, AnswerToAnswerCopier copier) {
        if (triggeredInstanceId == null) {
            throw new DDPException("Need to know the current triggered instance for copying from previous instance");
        }

        var instanceDao = handle.attach(ActivityInstanceDao.class);
        Long previousInstanceId = instanceDao.findMostRecentInstanceBeforeCurrent(triggeredInstanceId).orElse(null);
        if (previousInstanceId == null) {
            log.info("No previous instance for triggered instance {} to copy answers from", triggeredInstanceId);
            return;
        }

        Map<Long, FormResponse> instances;
        try (var stream = instanceDao.findFormResponsesWithAnswersByInstanceIds(Set.of(triggeredInstanceId, previousInstanceId))) {
            instances = stream.collect(Collectors.toMap(ActivityResponse::getId, Function.identity()));
        }
        FormResponse currentInstance = instances.get(triggeredInstanceId);
        FormResponse previousInstance = instances.get(previousInstanceId);
        if (currentInstance == null || previousInstance == null) {
            throw new DDPException("Could not find triggered or previous instance");
        }

        Map<String, QuestionDto> questionDtos = retrievePreviousInstanceQuestionDtos(handle, config, previousInstance);
        if (questionDtos.isEmpty()) {
            log.info("Previous instance {} does not have any answers to copy from", previousInstance.getGuid());
            return;
        }

        Iterable<String> questionsToCopy;
        if (!config.getPreviousInstanceFilters().isEmpty()) {
            // Create iterable so we copy answers in execution order of filters, ensuring that those
            // questions have answers in previous instance (by checking existence of questionDto).
            questionsToCopy = config.getPreviousInstanceFilters().stream()
                    .map(item -> item.getLocation().getQuestionStableId())
                    .filter(questionDtos::containsKey)
                    .collect(Collectors.toList());
        } else {
            questionsToCopy = questionDtos.keySet();
        }

        int numCopied = 0;
        for (var questionStableId : questionsToCopy) {
            QuestionDto question = questionDtos.get(questionStableId);
            copier.copy(previousInstance, question, currentInstance, question);
            numCopied++;
        }

        log.info("Copied answers for {} questions from previous instance {} to instance {}",
                numCopied, previousInstance.getGuid(), currentInstance.getGuid());
    }

    private Map<String, QuestionDto> retrievePreviousInstanceQuestionDtos(
            Handle handle, CopyConfiguration config, FormResponse previousInstance) {
        // Build set of stable ids of questions that have an answer in the previous instance.
        // Copier doesn't support copying top-level composite answers, so we need to drill into child questions.
        Set<String> questionStableIds = previousInstance.getAnswers().stream()
                .flatMap(answer -> {
                    if (answer.getQuestionType() == QuestionType.COMPOSITE) {
                        return ((CompositeAnswer) answer).getValue().stream()
                                .flatMap(row -> row.getValues().stream())
                                .filter(Objects::nonNull)
                                .map(Answer::getQuestionStableId);
                    } else {
                        return Stream.of(answer.getQuestionStableId());
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> specifiedStableIds = config.getPreviousInstanceFilters().stream()
                .map(item -> item.getLocation().getQuestionStableId())
                .collect(Collectors.toSet());
        if (!specifiedStableIds.isEmpty()) {
            // If config has previous answer questions specified, then filter down the list to only those
            // that we want to copy over. This can be done using a set intersection operation.
            questionStableIds.retainAll(specifiedStableIds);
            log.info("Filtered previous instance answers to only questions specified in copy config {}", config.getId());
        }

        if (questionStableIds.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, QuestionDto> questionDtos;
        var jdbiQuestion = handle.attach(JdbiQuestion.class);
        try (var stream = jdbiQuestion.findLatestDtosByStudyIdAndQuestionStableIds(config.getStudyId(), questionStableIds)) {
            questionDtos = stream.collect(Collectors.toMap(QuestionDto::getStableId, Function.identity()));
        }

        return questionDtos;
    }

    private Map<String, QuestionDto> retrieveQuestionDtos(Handle handle, CopyConfiguration config) {
        Set<String> allQuestionStableIds = new HashSet<>();
        for (var pair : config.getPairs()) {
            CopyLocation source = pair.getSource();
            if (source.getType() == CopyLocationType.ANSWER) {
                allQuestionStableIds.add(((CopyAnswerLocation) source).getQuestionStableId());
            }
            CopyLocation target = pair.getTarget();
            if (target.getType() == CopyLocationType.ANSWER) {
                allQuestionStableIds.add(((CopyAnswerLocation) target).getQuestionStableId());
            }
        }
        try (var questionStream = handle.attach(JdbiQuestion.class)
                .findLatestDtosByStudyIdAndQuestionStableIds(config.getStudyId(), allQuestionStableIds)) {
            return questionStream
                    .collect(Collectors.toMap(QuestionDto::getStableId, Function.identity()));
        }
    }

    private Map<Long, FormResponse> retrieveActivityData(Handle handle, long participantId, List<QuestionDto> questionDtos) {
        Set<Long> allActivityIds = new HashSet<>();
        for (var questionDto : questionDtos) {
            allActivityIds.add(questionDto.getActivityId());
        }

        Map<Long, FormResponse> container = new HashMap<>();
        try (var responseStream = handle.attach(ActivityInstanceDao.class)
                .findFormResponsesSubsetWithAnswersByUserId(participantId, allActivityIds)) {
            responseStream
                    .forEach(response -> {
                        long activityId = response.getActivityId();
                        FormResponse current = container.get(activityId);
                        if (current == null) {
                            container.put(activityId, response);
                            return;
                        }

                        boolean keepCurrent = (triggeredInstanceId != null && current.getId() == triggeredInstanceId);
                        if (!keepCurrent && response.getCreatedAt() > current.getCreatedAt()) {
                            container.put(activityId, response);
                        }
                    });

            return container;
        }
    }
}
