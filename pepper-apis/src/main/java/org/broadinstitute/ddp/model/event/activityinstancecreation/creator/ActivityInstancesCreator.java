package org.broadinstitute.ddp.model.event.activityinstancecreation.creator;

import java.util.List;

import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.event.activityinstancecreation.ActivityInstanceCreationEventSyncProcessor;

/**
 * Defines a creator which creates activity instances from answers entered in a current activity.
 * It should do the following steps:
 * - create instances (count of instances to be created calculated from entered answers and depends on a type of answers);
 * - copy answers from a source (current) activity instance to target (created) activity instances (if it needed).
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

    public abstract void createActivityInstances(List<Answer> sourceAnswers, ActivityDto activityDto, Integer numberOfActivitiesLeft);
}
