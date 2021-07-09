package org.broadinstitute.ddp.model.event.activityinstancecreation.creator;

import static java.lang.String.format;

import org.broadinstitute.ddp.db.dao.JdbiQuestionCached;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.jdbi.v3.core.Handle;


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


}
