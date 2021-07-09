package org.broadinstitute.ddp.model.event.activityinstancecreation.creator;

import static org.broadinstitute.ddp.model.event.activityinstancecreation.ActivityInstanceCreatorUtil.cloneAnswer;
import static org.broadinstitute.ddp.model.event.activityinstancecreation.ActivityInstanceCreatorUtil.getQuestionDto;

import java.util.List;

import org.broadinstitute.ddp.db.dao.AnswerDao;
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

    /**
     * Number of activity instances to be created = number of answers
     */
    @Override
    public int getInstancesCount(List<Answer> sourceAnswers) {
        return sourceAnswers.size();
    }

    /**
     * For each of created instance pass next processed answer
     */
    @Override
    protected Answer answerToCopyToTarget(List<Answer> sourceAnswers, int ind) {
        return sourceAnswers.get(ind);
    }

    /**
     * Copy an answer from source instance to target instance (in target instance should exist a picklist with same
     * options as in source question, and it should be possible to create an answer with same option stable_id).
     * We expect that answer - a PickListAnswer inside a CompositeAnswer.
     *
     * @param targetInstanceId ID of target activity instance
     * @param sourceAnswer     source answer (in question `sourceQuestionStableId`) to be copied to question
     *                         `targetQuestionStableId`
     */
    @Override
    protected void copyAnswerToNewActivityInstance(Handle handle, EventSignal signal, long targetInstanceId, Answer sourceAnswer) {
        var targetAnswer = cloneAnswer(sourceAnswer, sourceQuestionStableId, targetQuestionStableId);
        var questionDto = getQuestionDto(handle, signal.getStudyId(), targetQuestionStableId);
        handle.attach(AnswerDao.class).createAnswer(signal.getOperatorId(), targetInstanceId, questionDto.getId(), targetAnswer);
    }
}
