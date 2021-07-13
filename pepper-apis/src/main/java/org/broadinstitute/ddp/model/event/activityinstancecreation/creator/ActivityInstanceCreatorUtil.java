package org.broadinstitute.ddp.model.event.activityinstancecreation.creator;

import static java.lang.String.format;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.JdbiQuestionCached;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.jdbi.v3.core.Handle;


/**
 * Activity instance creation utility methods.
 */
public class ActivityInstanceCreatorUtil {

    public static QuestionDto getQuestionDto(Handle handle, long studyId, String questionStableId) {
        return  new JdbiQuestionCached(handle).findLatestDtoByStudyIdAndQuestionStableId(studyId, questionStableId)
                .orElseThrow(() -> new DDPException(format(
                        "Could not find target question with stable id %s in study %d", studyId, questionStableId)));
    }

    public static Answer getAnswer(Handle handle, long instanceId, String questionStableId) {
        return handle.attach(AnswerDao.class).findAnswerByInstanceIdAndQuestionStableId(instanceId, questionStableId)
                .orElseThrow(() -> new DaoException(format(
                        "Error to detect answer: question stableId=%s, instanceId=%d", questionStableId, instanceId)));
    }
}
