package org.broadinstitute.ddp.model.event.activityinstancecreation;

import static java.lang.String.format;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.event.ActivityInstanceStatusChangeSignal;
import org.broadinstitute.ddp.model.event.EventSignal;
import org.broadinstitute.ddp.model.event.activityinstancecreation.creator.ActivityInstancesCreator;
import org.broadinstitute.ddp.model.event.activityinstancecreation.creator.ActivityInstancesFromCompositeCreator;
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
public class ActivityInstanceCreationEventSyncProcessorFromAnswers extends ActivityInstanceCreationEventSyncProcessorDefault {

    private final String sourceQuestionStableId;
    private final String targetQuestionStableId;

    public ActivityInstanceCreationEventSyncProcessorFromAnswers(
            Handle handle,
            EventSignal signal,
            long studyActivityId,
            String sourceQuestionStableId,
            String targetQuestionStableId,
            ActivityInstanceCreationService creationService) {
        super(handle, signal, studyActivityId, creationService);
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
    public void create() {
        var activityDto = jdbiActivity.queryActivityById(studyActivityId);
        creationService.checkSignalIfNestedTargetActivity(activityDto.getParentActivityId());

        // check if number of non-used activities is enough (it can be limited if a special limit parameter specified)
        Integer numberOfActivitiesLeft = creationService.detectNumberOfActivitiesLeft(studyActivityId, activityDto, jdbiActivityInstance);
        if (numberOfActivitiesLeft == null || numberOfActivitiesLeft > 0) {

            long sourceActivityInstanceId = ((ActivityInstanceStatusChangeSignal) signal).getActivityInstanceIdThatChanged();

            List<Answer> sourceAnswers = detectSelectedAnswers(handle, sourceActivityInstanceId);

            var questionType = detectQuestionType(sourceAnswers);
            ActivityInstancesCreator activityInstancesCreator;
            switch (questionType) {
                case COMPOSITE:
                    activityInstancesCreator = new ActivityInstancesFromCompositeCreator(
                            this, sourceQuestionStableId, targetQuestionStableId);
                    break;
                default:
                    throw new DDPException("Not supported creation of activity instances from answers for type=" + questionType);
            }
            activityInstancesCreator.createActivityInstances(sourceAnswers, activityDto, numberOfActivitiesLeft);
        }
    }

    /**
     * Detect an array with child answers inside a composite answer.
     * Currently supported only composite answers (i.e. `sourceQuestionStableId` - should be
     * stable_ID of a Composite Question.
     */
    private List<Answer> detectSelectedAnswers(Handle handle, long sourceActivityInstanceId) {
        var answer = getAnswer(handle, sourceActivityInstanceId, sourceQuestionStableId);
        switch (answer.getQuestionType()) {
            case COMPOSITE:
                return getAnswersFromComposite((CompositeAnswer) answer);
            default:
                throw new DDPException("Activity instances creation from answers is supported only for composite questions. StableID="
                        + sourceQuestionStableId);
        }
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

    /**
     * Detect question type.
     * Assume that all answers in the list are of the same type.
     */
    private QuestionType detectQuestionType(List<Answer> answers) {
        return !answers.isEmpty() ? answers.get(0).getQuestionType() : null;
    }
}
