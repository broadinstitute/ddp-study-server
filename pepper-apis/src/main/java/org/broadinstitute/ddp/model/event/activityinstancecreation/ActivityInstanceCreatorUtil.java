package org.broadinstitute.ddp.model.event.activityinstancecreation;

import static java.lang.String.format;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.JdbiQuestionCached;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.jdbi.v3.core.Handle;


/**
 * Activity instance creation utility methods.
 */
public class ActivityInstanceCreatorUtil {

    public static Answer cloneAnswer(Answer answer, String sourceQuestionStableId, String targetQuestionStableId) {
        if (!(answer instanceof PicklistAnswer)) {
            throw new DDPException("Source answer should be of type PicklistAnswer, StableID=" + sourceQuestionStableId);
        }
        PicklistAnswer picklistAnswer = (PicklistAnswer) answer;
        return new PicklistAnswer(null, targetQuestionStableId, null, picklistAnswer.getValue());
    }

    public static QuestionDto getQuestionDto(Handle handle, long studyId, String questionStableId) {
        return handle.attach(JdbiQuestionCached.class).findLatestDtoByStudyIdAndQuestionStableId(studyId, questionStableId)
                .orElseThrow(() -> new DDPException(format(
                        "Could not find target question with stable id %s in study %d", studyId, questionStableId)));
    }

    public static Answer getAnswer(Handle handle, long instanceId, String questionStableId) {
        return handle.attach(AnswerDao.class).findAnswerByInstanceIdAndQuestionStableId(instanceId, questionStableId)
                .orElseThrow(() -> new DaoException(format(
                        "Error to detect answer: question stableId=%s, instanceId=%d", questionStableId, instanceId)));
    }

    public static List<Answer> getAnswersFromComposite(CompositeAnswer answer) {
        return Optional.of(answer).stream()
                .flatMap(parent -> parent.getValue().stream())
                .flatMap(row -> row.getValues().stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
