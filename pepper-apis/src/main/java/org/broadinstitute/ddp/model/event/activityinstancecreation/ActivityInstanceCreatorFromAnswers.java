package org.broadinstitute.ddp.model.event.activityinstancecreation;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstanceStatus;
import org.broadinstitute.ddp.db.dao.JdbiQuestionCached;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.event.EventSignal;
import org.broadinstitute.ddp.service.ActivityInstanceCreationService;
import org.jdbi.v3.core.Handle;

/**
 * Create  activity instances from the selected answers of parent instances.
 * Question stable_id which answers to check is specified by parameter {@link #sourceQuestionStableId}.
 *
 * <p><b>Algorithm:</b>
 * <ul>
 *     <li>find answer (picklist) by 'sourceQuestionStableId' and detect selected options;</li>
 *     <li>for each of selected options:</li>
 *     <li> - create a new instance (of activity with ID='studyActivityId';</li>
 *     <li> - if `targetQuestionStableId` is specified then copy source answers to target answers;</li>
 * </ul>
 */
public class ActivityInstanceCreatorFromAnswers extends ActivityInstanceCreatorDefault {

    private final String sourceQuestionStableId;
    private final String targetQuestionStableId;

    public ActivityInstanceCreatorFromAnswers(
            long studyActivityId,
            String sourceQuestionStableId,
            String targetQuestionStableId,
            ActivityInstanceCreationService creationService) {
        super(studyActivityId, creationService);
        this.sourceQuestionStableId = sourceQuestionStableId;
        this.targetQuestionStableId = targetQuestionStableId;
    }

    /**
     * Create N activity instances.
     * where N = number of answers in composite question `sourceQuestionStableId`.
     * IF N > not_used_activities_count THEN N = not_used_activities_count,
     * where not_used_activities_count = max_allowed_activity_instances - created_activity_instances.
     */
    @Override
    public void create(Handle handle, EventSignal signal) {
        JdbiActivityInstance jdbiActivityInstance = handle.attach(JdbiActivityInstance.class);
        JdbiActivityInstanceStatus jdbiActivityInstanceStatus = handle.attach(JdbiActivityInstanceStatus.class);
        JdbiActivity jdbiActivity = handle.attach(JdbiActivity.class);

        ActivityDto activityDto = jdbiActivity.queryActivityById(studyActivityId);
        creationService.checkSignalIfNestedTargetActivity(activityDto);

        // detect parent instance id
        Long parentInstanceId = creationService.detectParentInstanceId(activityDto);
        if (parentInstanceId == null) {
            throw new DDPException("Failed to detect parent instance ID");
        }

        // check if number of non-used activities is enough (it can be limited if a special limit parameter specified)
        Integer numberOfActivitiesLeft = creationService.detectNumberOfActivitiesLeft(studyActivityId, activityDto, jdbiActivityInstance);
        if (numberOfActivitiesLeft == null || numberOfActivitiesLeft > 0) {

            List<Answer> sourceAnswers = detectSelectedAnswers(handle, parentInstanceId);

            // detect number of new activities to be created
            int newInstancesCount = sourceAnswers.size();
            if (newInstancesCount < numberOfActivitiesLeft) {
                newInstancesCount = numberOfActivitiesLeft;
            }

            creationService.hideExistingInstancesIfRequired(studyActivityId, handle, activityDto);

            // create child instances and copy source answers to target question in a created activities
            for (int i = 0; i < newInstancesCount; i++) {

                long newActivityInstanceId =
                        createActivityInstance(handle, signal, jdbiActivityInstance, jdbiActivityInstanceStatus, parentInstanceId);

                if (isNotBlank(targetQuestionStableId)) {
                    copyAnswerToNewActivityInstance(handle, signal, newActivityInstanceId, sourceAnswers.get(i));
                }
            }
        }
    }

    /**
     * Detect an array with child answers inside a composite answer.
     * Currently supported only composite answers (i.e. `sourceQuestionStableId` - should be
     * stable_ID of a Composite Question.
     */
    private List<Answer> detectSelectedAnswers(Handle handle, long parentInstanceId) {
        Answer answer = getAnswer(handle, parentInstanceId, sourceQuestionStableId);
        if (answer instanceof CompositeAnswer) {
            return getAnswersFromComposite((CompositeAnswer)answer);
        } else {
            throw new DDPException("Activity instances creation from answers is supported only for composite questions. StableID="
                    + sourceQuestionStableId);
        }
    }

    /**
     * Copy answer from source instance to target instance (in target instance should exist a picklist with same
     * options as in source question, and it should be possible to create an answer with same option stable_id).
     * We expect than answer - PickListAnswer (i.e. currently we deal only with picklist-inside-composite).
     *
     * @param targetInstanceId ID of target activity instance
     * @param sourceAnswer     source answer (in question `sourceQuestionStableId`) to be copied to question
     *                         `targetQuestionStableId`
     */
    private void copyAnswerToNewActivityInstance(Handle handle, EventSignal signal, long targetInstanceId, Answer sourceAnswer) {
        var targetAnswer = cloneAnswer(sourceAnswer, sourceQuestionStableId, targetQuestionStableId);
        var questionDto = getQuestionDto(handle, signal.getStudyId(), targetQuestionStableId);
        handle.attach(AnswerDao.class).createAnswer(signal.getOperatorId(), targetInstanceId, questionDto.getId(), targetAnswer);
    }

    private static Answer cloneAnswer(Answer answer, String sourceQuestionStableId, String targetQuestionStableId) {
        if (!(answer instanceof PicklistAnswer)) {
            throw new DDPException("Source answer should be of type PicklistAnswer, StableID=" + sourceQuestionStableId);
        }
        PicklistAnswer picklistAnswer = (PicklistAnswer) answer;
        return new PicklistAnswer(null, targetQuestionStableId, null, picklistAnswer.getValue());
    }

    private static QuestionDto getQuestionDto(Handle handle, long studyId, String questionStableId) {
        return handle.attach(JdbiQuestionCached.class).findLatestDtoByStudyIdAndQuestionStableId(studyId, questionStableId)
                .orElseThrow(() -> new DDPException(String.format(
                        "Could not find target question with stable id %s in study %d", studyId, questionStableId)));
    }

    private static Answer getAnswer(Handle handle, long instanceId, String questionStableId) {
        return handle.attach(AnswerDao.class).findAnswerByInstanceIdAndQuestionStableId(instanceId, questionStableId)
                .orElseThrow(() -> new DaoException(format(
                        "Error to detect answer: question stableId=%s, instanceId=%d", questionStableId, instanceId)));
    }

    private static List<Answer> getAnswersFromComposite(CompositeAnswer answer) {
        return Optional.of(answer).stream()
                .flatMap(parent -> parent.getValue().stream())
                .flatMap(row -> row.getValues().stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
