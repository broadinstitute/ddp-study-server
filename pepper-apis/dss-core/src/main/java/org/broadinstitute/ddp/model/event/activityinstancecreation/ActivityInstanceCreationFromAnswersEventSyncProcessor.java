package org.broadinstitute.ddp.model.event.activityinstancecreation;

import static java.lang.String.format;
import static org.broadinstitute.ddp.model.event.activityinstancecreation.creator.ActivityInstancesFromCompositeCreator.getChildAnswersFromComposite;
import static org.broadinstitute.ddp.util.QuestionUtil.getAnswer;

import java.util.List;

import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiQuestionCached;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
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
 *     <li> - if `targetQuestionStableId` is specified then copy the processed answer to `targetQuestionStableId`;</li>
 * </ul>
 * NOTE: currently is supported the COMPOSITE source (i.e. 'sourceQuestionStableId' points to a CompositeQuestion
 * containing PickList of render mode AUTOCOMPLETE). And in this case `targetQuestionStableId` should point to a
 * PickList also. Both source and target picklists should contain same options. In addition to options it could be possible
 * that a user enter some custom text (detail text).
 */
public class ActivityInstanceCreationFromAnswersEventSyncProcessor extends ActivityInstanceCreationEventSyncProcessor {

    private final String sourceQuestionStableId;
    private final String targetQuestionStableId;

    /**
     * Constructor.
     *
     * @param handle                 jdbi Handle
     * @param signal                 EventSignal (here should be of type ActivityInstanceStatusChangeSignal)
     * @param studyActivityId        ID of a study activity which instance to create
     * @param sourceQuestionStableId stable_id of a question in source activity (from which to copy answers)
     * @param targetQuestionStableId stable_id of a question in target (created ones) activities (where to copy answers)
     * @param creationService        service class containing methods used during activity instance creation
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
     * Create N activity instances from answers entered in current activity.
     * where N = number of answers in composite question `sourceQuestionStableId`.
     */
    @Override
    public void processInstancesCreation() {
        activityDto = jdbiActivity.queryActivityById(studyActivityId);
        creationService.checkSignalIfNestedTargetActivity(activityDto.getParentActivityId());
        var sourceActivityInstanceId = detectSourceInstanceId();
        var parentAnswer = getAnswer(handle, sourceActivityInstanceId, sourceQuestionStableId);
        var sourceAnswers = detectSourceAnswers(parentAnswer);

        var activityInstancesCreator = detectActivityInstancesCreator(parentAnswer);
        if (detectPossibleNumberOfInstancesToCreate(activityInstancesCreator.getInstancesCount(sourceAnswers)) > 0) {
            creationService.hideExistingInstancesIfRequired(studyActivityId, handle, activityDto);
            activityInstancesCreator.createActivityInstances(sourceAnswers, activityDto);
        }
    }

    /**
     * Check if the necessary number of activities is possible to create. If not - an exception is thrown
     */
    @Override
    public int detectPossibleNumberOfInstancesToCreate(int instancesToCreate) {
        var numberOfActivitiesLeft = creationService.detectNumberOfActivitiesLeft(studyActivityId, activityDto, jdbiActivityInstance);
        if (numberOfActivitiesLeft != null && numberOfActivitiesLeft < instancesToCreate) {
            throw new DDPException(format("Impossible to create all %d instances from answers: the limit of instances "
                    + "of this type is gained", instancesToCreate));
        } else {
            return instancesToCreate;
        }
    }

    /**
     * Detect the source activity instance to use for creating instances and copying answers. Questions are associated
     * with the activity they're used in, so if the signal's activity matches with the source question, then we'll use
     * that. Otherwise, we lookup the latest activity instance for the source question.
     */
    private long detectSourceInstanceId() {
        QuestionDto questionDto = new JdbiQuestionCached(handle)
                .findLatestDtoByStudyIdAndQuestionStableId(signal.getStudyId(), sourceQuestionStableId)
                .orElseThrow(() -> new DDPException("Unable to find source question with stable id " + sourceQuestionStableId));
        long sourceActivityId = questionDto.getActivityId();

        if (signal.getEventTriggerType() == EventTriggerType.ACTIVITY_STATUS) {
            var statusSignal = (ActivityInstanceStatusChangeSignal) signal;
            if (statusSignal.getActivityIdThatChanged() == sourceActivityId) {
                return statusSignal.getActivityInstanceIdThatChanged();
            }
        }

        return handle.attach(JdbiActivityInstance.class)
                .findLatestInstanceIdByUserGuidAndActivityId(signal.getParticipantGuid(), sourceActivityId)
                .orElseThrow(() -> new DDPException(
                        "Unable to find source activity instance with question stable id " + sourceQuestionStableId));
    }

    /**
     * Detect an array with answers in source question (pointed by `sourceQuestionStableId`).
     * Currently supported only composite answers (i.e. `sourceQuestionStableId` - should be
     * stable_ID of a Composite Question).
     */
    private List<Answer> detectSourceAnswers(Answer parentAnswer) {
        switch (parentAnswer.getQuestionType()) {
            case COMPOSITE:
                return getChildAnswersFromComposite((CompositeAnswer) parentAnswer);
            default:
                throw new DDPException("Activity instances creation from answers is supported only for composite questions. StableID="
                        + sourceQuestionStableId);
        }
    }

    /**
     * Depending on a question type choose an appropriate creator (now supported only COMPOSITE creator)
     */
    private ActivityInstancesCreator detectActivityInstancesCreator(Answer parentAnswer) {
        ActivityInstancesCreator activityInstancesCreator;
        switch (parentAnswer.getQuestionType()) {
            case COMPOSITE:
                activityInstancesCreator = new ActivityInstancesFromCompositeCreator(
                        this, sourceQuestionStableId, targetQuestionStableId);
                break;
            default:
                throw new DDPException("Not supported creation of activity instances from answers for type="
                        + parentAnswer.getQuestionType());
        }
        return activityInstancesCreator;
    }
}
