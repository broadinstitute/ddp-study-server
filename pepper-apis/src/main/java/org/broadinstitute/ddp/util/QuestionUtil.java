package org.broadinstitute.ddp.util;

import static java.lang.String.format;

import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserActivityInstanceSummary;
import org.broadinstitute.ddp.json.form.BlockVisibility;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.service.FormActivityService;
import org.jdbi.v3.core.Handle;
import spark.Response;

import java.util.List;
import java.util.Optional;

public class QuestionUtil {

    /**
     * Checks if a question is read-only
     *
     * @param handle               JDBI handle
     * @param dto                  QuestionDto
     * @param activityInstanceGuid GUID of the activity instance to check
     * @return The result of the check
     s*/
    public static boolean isReadonly(Handle handle, QuestionDto dto, String activityInstanceGuid) {
        boolean isWriteOnce = dto.isWriteOnce();
        if (!isWriteOnce) {
            return false;
        }

        ActivityInstanceDto activityInstanceDto = handle.attach(JdbiActivityInstance.class)
                .getByActivityInstanceGuid(activityInstanceGuid).orElseThrow(() -> new DaoException(String.format(
                        "Could not find activity instance with guid=%s while getting question with id=%d and stableId=%s",
                        activityInstanceGuid, dto.getId(), dto.getStableId())));

        InstanceStatusType statusType = activityInstanceDto.getStatusType();
        if (InstanceStatusType.COMPLETE.equals(statusType)) {
            return true;
        }

        // If it is not the first instance - block editing
        var instanceDao = handle.attach(ActivityInstanceDao.class);
        Long previousInstanceId = instanceDao.findMostRecentInstanceBeforeCurrent(activityInstanceDto.getId())
                .orElse(null);
        return previousInstanceId != null;
    }

    /**
     * Check if a question is read-only. This is an alternative version that might be faster depending on what data is available.
     *
     * @param handle the database handle
     * @param questionDef the question definition
     * @param instanceDto the activity instance dto
     * @return true if read-only, otherwise false
     */
    public static boolean isReadonly(Handle handle, QuestionDef questionDef, ActivityInstanceDto instanceDto) {
        boolean isWriteOnce = questionDef.isWriteOnce();
        if (!isWriteOnce) {
            return false;
        }

        InstanceStatusType statusType = instanceDto.getStatusType();
        if (InstanceStatusType.COMPLETE.equals(statusType)) {
            return true;
        }

        // If it is not the first instance - block editing
        Long previousInstanceId = handle.attach(ActivityInstanceDao.class)
                .findMostRecentInstanceBeforeCurrent(instanceDto.getId())
                .orElse(null);
        return previousInstanceId != null;
    }

    /**
     * Detect if {@link Question} is read-only.
     * PreviousInstanceId already calculated and passed as a parameter.
     */
    public static boolean isReadOnly(QuestionDef questionDef, InstanceStatusType instanceStatusType, Long previousInstanceId) {
        if (!questionDef.isWriteOnce()) {
            return false;
        }
        if (InstanceStatusType.COMPLETE.equals(instanceStatusType)) {
            return true;
        }
        return previousInstanceId != null;
    }

    public static Answer getAnswer(Handle handle, long instanceId, String questionStableId) {
        return handle.attach(AnswerDao.class).findAnswerByInstanceIdAndQuestionStableId(instanceId, questionStableId)
                .orElseThrow(() -> new DaoException(format(
                        "Error to detect answer: question stableId=%s, instanceId=%d", questionStableId, instanceId)));
    }

    public static Optional<List<BlockVisibility>> getBlockVisibility(Handle handle, Response response,
                                                                     String parentInstanceGuid, User participantUser, StudyDto studyDto,
                                                                     String operatorGuid, boolean isStudyAdmin) {

        PexInterpreter interpreter = new TreeWalkInterpreter();
        FormActivityService formService = new FormActivityService(interpreter);

        List<org.broadinstitute.ddp.json.form.BlockVisibility> result = null;

        if (parentInstanceGuid == null) {
            return Optional.ofNullable(result);
        }

        ActivityInstanceDto parentInstanceDto = RouteUtil.findAccessibleInstanceOrHalt(
                response, handle, participantUser, studyDto,
                parentInstanceGuid, isStudyAdmin);

        if (parentInstanceDto != null) {

            ActivityDefStore activityStore = ActivityDefStore.getInstance();
            FormActivityDef parentActivity = ActivityInstanceUtil
                    .getActivityDef(handle, activityStore, parentInstanceDto, studyDto.getGuid());

            UserActivityInstanceSummary instanceSummary = RouteUtil.findUserActivityInstanceSummaryOrHalt(
                    response, handle, participantUser.getGuid(), studyDto.getGuid(), parentInstanceGuid, isStudyAdmin);
            result = formService.getBlockVisibilities(handle, instanceSummary, parentActivity,
                    participantUser.getGuid(), operatorGuid, parentInstanceGuid);
        }

        return Optional.ofNullable(result);
    }
}
