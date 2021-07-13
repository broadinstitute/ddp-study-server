package org.broadinstitute.ddp.model.event.activityinstancecreation.creator;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
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
        var targetAnswer = cloneChildAnswer(sourceAnswer, sourceQuestionStableId, targetQuestionStableId);
        handle.attach(AnswerDao.class).createAnswer(signal.getOperatorId(), targetInstanceId, targetAnswer);
    }

    /**
     * Create a PicklistAnswer from a child (Picklist option or detail text) answer of source instance. This answer to be saved
     * to a target new instance.
     */
    private Answer cloneChildAnswer(Answer answer, String sourceQuestionStableId, String targetQuestionStableId) {
        if (!(answer instanceof PicklistAnswer)) {
            throw new DDPException("Source answer should be of type PicklistAnswer, StableID=" + sourceQuestionStableId);
        }
        var picklistAnswer = (PicklistAnswer) answer;
        return new PicklistAnswer(null, targetQuestionStableId, null, picklistAnswer.getValue());
    }

    /**
     * Get child non-null answers from a composite answer.
     * The result List of Answers will be copied (one by one) to created activity instances.
     *
     * @param answer  a parent (composite) answer in source instance pointed by `sourceQuestionStableId`.
     */
    public static List<Answer> getChildAnswersFromComposite(CompositeAnswer answer) {
        return Optional.of(answer).stream()
                .flatMap(parent -> parent.getValue().stream())
                .flatMap(row -> row.getValues().stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
