package org.broadinstitute.ddp.model.event.activityinstancecreation.creator;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.broadinstitute.ddp.model.event.activityinstancecreation.creator.ActivityInstanceCreatorUtil.cloneAnswer;
import static org.broadinstitute.ddp.model.event.activityinstancecreation.creator.ActivityInstanceCreatorUtil.getQuestionDto;

import java.util.List;

import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.event.EventSignal;
import org.broadinstitute.ddp.model.event.activityinstancecreation.ActivityInstanceCreationEventSyncProcessor;
import org.jdbi.v3.core.Handle;

/**
 * Create activity instances from answers entered in question of type {@link QuestionType#COMPOSITE}.
 */
public class ActivityInstancesFromCompositeCreator extends ActivityInstancesCreator {

    public ActivityInstancesFromCompositeCreator(
            ActivityInstanceCreationEventSyncProcessor eventProcessor,
            String sourceQuestionStableId,
            String targetQuestionStableId) {
        super(eventProcessor, sourceQuestionStableId, targetQuestionStableId);
    }

    @Override
    public void createActivityInstances(List<Answer> sourceAnswers, ActivityDto activityDto, Integer numberOfActivitiesLeft) {
        // detect number of new activities to be created
        if (sourceAnswers.size() < numberOfActivitiesLeft) {
            throw new DDPException(format("Impossible to create all %d instances from answers: the limit of instances "
                    + "of this type is gained", sourceAnswers.size()));
        }

        eventProcessor.getCreationService().hideExistingInstancesIfRequired(eventProcessor.getStudyActivityId(),
                eventProcessor.getHandle(), activityDto);

        // create child instances and copy source answers to target question in the created activities
        for (int i = 0; i < sourceAnswers.size(); i++) {

            long newActivityInstanceId = eventProcessor.createActivityInstance(activityDto.getParentActivityId());

            if (isNotBlank(targetQuestionStableId)) {
                copyAnswerToNewActivityInstance(
                        eventProcessor.getHandle(),
                        eventProcessor.getSignal(),
                        newActivityInstanceId,
                        sourceAnswers.get(i));
            }
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
}
