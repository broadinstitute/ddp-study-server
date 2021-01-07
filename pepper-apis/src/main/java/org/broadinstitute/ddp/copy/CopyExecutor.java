package org.broadinstitute.ddp.copy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
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
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CopyExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(CopyExecutor.class);

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

        for (var pair : config.getPairs()) {
            CopyLocation source = pair.getSource();
            CopyLocation target = pair.getTarget();

            if (source.getType() == CopyLocationType.ANSWER && target.getType() == CopyLocationType.ANSWER) {
                String sourceStableId = ((CopyAnswerLocation) source).getQuestionStableId();
                QuestionDto sourceQuestion = questionDtosByStableId.get(sourceStableId);
                FormResponse sourceInstance = responsesById.get(sourceQuestion.getActivityId());

                String targetStableId = ((CopyAnswerLocation) target).getQuestionStableId();
                QuestionDto targetQuestion = questionDtosByStableId.get(targetStableId);
                FormResponse targetInstance = responsesById.get(targetQuestion.getActivityId());

                ansToAnsCopier.copy(sourceInstance, sourceQuestion, targetInstance, targetQuestion);
            } else if (source.getType() == CopyLocationType.ANSWER) {
                String sourceStableId = ((CopyAnswerLocation) source).getQuestionStableId();
                QuestionDto sourceQuestion = questionDtosByStableId.get(sourceStableId);
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
            LOG.info("No previous instance for triggered instance {} to copy answers from", triggeredInstanceId);
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

        // Build set of stable ids of questions that have an answer in the previous instance.
        // Copier doesn't support copying top-level composite answers, so we need to drill into child questions.
        Map<String, Set<String>> childQuestionStableIds = new HashMap<>();
        Set<String> questionStableIds = previousInstance.getAnswers().stream()
                .map(answer -> {
                    if (answer.getQuestionType() == QuestionType.COMPOSITE) {
                        Set<String> childStableId = ((CompositeAnswer) answer).getValue().stream()
                                .flatMap(row -> row.getValues().stream())
                                .filter(Objects::nonNull)
                                .map(Answer::getQuestionStableId)
                                .collect(Collectors.toSet());
                        childQuestionStableIds.put(answer.getQuestionStableId(), childStableId);
                        return null;
                    } else {
                        return answer.getQuestionStableId();
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (var childStableIds : childQuestionStableIds.values()) {
            questionStableIds.addAll(childStableIds);
        }

        Set<String> specifiedStableIds = config.getPreviousInstanceFilters().stream()
                .map(item -> item.getLocation().getQuestionStableId())
                .collect(Collectors.toSet());
        if (!specifiedStableIds.isEmpty()) {
            // If config has previous answer questions specified, then filter down the list to only those
            // that we want to copy over. This can be done using a set intersection operation.
            questionStableIds.retainAll(specifiedStableIds);
            LOG.info("Filtered previous instance answers to only questions specified in copy config {}", config.getId());
        }

        if (questionStableIds.isEmpty()) {
            LOG.info("Previous instance {} does not have any answers to copy from", previousInstance.getGuid());
            return;
        }

        Map<String, QuestionDto> questionDtos;
        var jdbiQuestion = handle.attach(JdbiQuestion.class);
        try (var stream = jdbiQuestion.findLatestDtosByStudyIdAndQuestionStableIds(config.getStudyId(), questionStableIds)) {
            questionDtos = stream.collect(Collectors.toMap(QuestionDto::getStableId, Function.identity()));
        }

        int numCopied = 0;
        for (var previousAnswer : previousInstance.getAnswers()) {
            String stableId = previousAnswer.getQuestionStableId();
            if (previousAnswer.getQuestionType() == QuestionType.COMPOSITE) {
                boolean copiedChild = false;
                for (var childStableId : childQuestionStableIds.get(stableId)) {
                    if (specifiedStableIds.isEmpty() || specifiedStableIds.contains(childStableId)) {
                        QuestionDto childQuestion = questionDtos.get(childStableId);
                        copier.copy(previousInstance, childQuestion, currentInstance, childQuestion);
                        copiedChild = true;
                    }
                }
                if (copiedChild) {
                    numCopied++;
                }
            } else if (specifiedStableIds.isEmpty() || specifiedStableIds.contains(stableId)) {
                QuestionDto question = questionDtos.get(stableId);
                copier.copy(previousInstance, question, currentInstance, question);
                numCopied++;
            }
        }

        LOG.info("Copied {} answers from previous instance {} to instance {}",
                numCopied, previousInstance.getGuid(), currentInstance.getGuid());
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
