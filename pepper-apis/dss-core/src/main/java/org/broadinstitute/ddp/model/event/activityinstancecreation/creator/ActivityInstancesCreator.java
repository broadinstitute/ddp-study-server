package org.broadinstitute.ddp.model.event.activityinstancecreation.creator;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;

import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.event.EventSignal;
import org.broadinstitute.ddp.model.event.activityinstancecreation.ActivityInstanceCreationEventSyncProcessor;
import org.jdbi.v3.core.Handle;

/**
 * Defines a creator which creates activity instances from answers entered in a current activity.
 * It should do the following steps:
 * <pre>
 * - create instances (count of instances to be created calculated from entered answers and depends on a type of answers);
 * - copy answers from a source (current) activity instance to target (created) activity instances (if it needed).
 * </pre>
 */
public abstract class ActivityInstancesCreator {

    protected final ActivityInstanceCreationEventSyncProcessor eventProcessor;
    protected final String sourceQuestionStableId;
    protected final String targetQuestionStableId;

    public ActivityInstancesCreator(
            ActivityInstanceCreationEventSyncProcessor eventProcessor,
            String sourceQuestionStableId,
            String targetQuestionStableId) {
        this.eventProcessor = eventProcessor;
        this.sourceQuestionStableId = sourceQuestionStableId;
        this.targetQuestionStableId = targetQuestionStableId;
    }

    /**
     * Create activity instances and copy source answers to target question in the created instances
     */
    public void createActivityInstances(List<Answer> sourceAnswers, ActivityDto activityDto) {
        var instancesCount = getInstancesCount(sourceAnswers);

        for (int i = 0; i < instancesCount; i++) {

            long newActivityInstanceId = eventProcessor.createActivityInstance(activityDto.getParentActivityId());

            if (isNotBlank(targetQuestionStableId)) {
                copyAnswerToNewActivityInstance(
                        eventProcessor.getHandle(),
                        eventProcessor.getSignal(),
                        newActivityInstanceId,
                        answerToCopyToTarget(sourceAnswers, i));
            }
        }
    }

    /**
     * Detect number of activity instances to be created
     */
    public abstract int getInstancesCount(List<Answer> sourceAnswers);

    /**
     * Detect an answer which to be passed to a created instance with index 'ind'
     */
    protected abstract Answer answerToCopyToTarget(List<Answer> sourceAnswers, int ind);

    /**
     * Copy a specified source answer to a specified instance (to question pointed by `targetQuestionStableId`).
     */
    protected abstract void copyAnswerToNewActivityInstance(Handle handle, EventSignal signal, long targetInstanceId, Answer sourceAnswer);
}
