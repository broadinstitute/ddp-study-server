package org.broadinstitute.ddp.model.event.activityinstancecreation;

import static org.broadinstitute.ddp.model.event.activityinstancecreation.ActivityInstanceCreatorUtil.getAnswer;
import static org.broadinstitute.ddp.model.event.activityinstancecreation.ActivityInstanceCreatorUtil.getAnswersFromComposite;

import java.util.List;

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
 * Create activity instances from the selected answers (selected in current instance).
 * Question stable_id which answers to check is specified by parameter {@link #sourceQuestionStableId}.
 *
 * <p><b>Algorithm:</b>
 * <ul>
 *     <li>find a question by 'sourceQuestionStableId' (in source, current activity) and get all answers (for example selections);</li>
 *     <li>for each of found answers:</li>
 *     <li> - create a new instance (of activity with ID='studyActivityId';</li>
 *     <li> - if `targetQuestionStableId` is specified then copy the processed answer to to `targetQuestionStableId`;</li>
 * </ul>
 * NOTE: currently is supported the COMPOSITE source (i.e. 'sourceQuestionStableId' points to a CompositeQuestion
 * containing PickList of render mode AUTOCOMPLETE). And in this case `targetQuestionStableId` should point to a
 * PickList also. Both source and target picklists should contain same options. In addition to options it could be possible
 * that a user enter some custom text (detail text).
 */
public class ActivityInstanceCreationFromAnswersEventSyncProcessor extends ActivityInstanceCreationEventSyncProcessorDefault {

    private final String sourceQuestionStableId;
    private final String targetQuestionStableId;

    /**
     * Constructor.
     * @param handle                  jdbi Handle
     * @param signal                  EventSignal (here should be of type ActivityInstanceStatusChangeSignal)
     * @param studyActivityId         ID of a study activity which instance to create
     * @param sourceQuestionStableId  stable_id of a question in source activity (from which to copy answers)
     * @param targetQuestionStableId  stable_id of a question in target (created ones) activities (where to copy answers)
     * @param creationService         service containins methods used during activity instance creation
     */
    public ActivityInstanceCreationFromAnswersEventSyncProcessor(
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

            // depending on a question type choose an appropriate creator (now supported only COMPOSITE creator)
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
     * Detect an array with answers in source question (pointed by `sourceQuestionStableId`).
     * Currently supported only composite answers (i.e. `sourceQuestionStableId` - should be
     * stable_ID of a Composite Question).
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

    /**
     * Detect question type.
     * Assume that all answers in the list are of the same type.
     */
    private QuestionType detectQuestionType(List<Answer> answers) {
        return !answers.isEmpty() ? answers.get(0).getQuestionType() : null;
    }
}
