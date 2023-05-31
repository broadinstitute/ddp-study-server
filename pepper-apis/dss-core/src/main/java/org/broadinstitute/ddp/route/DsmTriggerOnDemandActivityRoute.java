package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam;

import java.time.Instant;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerCachedDao;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.json.dsm.TriggerActivityPayload;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.util.ActivityInstanceUtil;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import spark.Request;
import spark.Response;

@Slf4j
public class DsmTriggerOnDemandActivityRoute extends ValidatedJsonInputRoute<TriggerActivityPayload> {
    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_BAD_REQUEST;
    }

    private static final String RESULT_FILE_STABLE_ID = "RESULT_FILE";
    private static final String RESULT_FILE_ACTIVITY_ID = "SOMATIC_RESULTS";


    @Override
    public Object handle(Request request, Response response, TriggerActivityPayload payload) {
        String studyGuid = request.params(PathParam.STUDY_GUID);
        String activityCode = request.params(PathParam.ACTIVITY_CODE);
        String participantGuidOrLegacyAltPid = payload.getParticipantGuid();

        log.info("Attempting to trigger on-demand activity for study guid {}, activity code {}, participant guid {}",
                studyGuid, activityCode, participantGuidOrLegacyAltPid);

        TransactionWrapper.useTxn(handle -> {
            long studyId = handle.attach(JdbiUmbrellaStudy.class)
                    .getIdByGuid(studyGuid)
                    .orElseGet(() -> {
                        ApiError err = new ApiError(ErrorCodes.STUDY_NOT_FOUND, "Could not find study with guid " + studyGuid);
                        log.warn(err.getMessage());
                        ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, err);
                        return -1L;     // Not reached
                    });

            ActivityDto activityDto = handle.attach(JdbiActivity.class)
                    .findActivityByStudyIdAndCode(studyId, activityCode)
                    .orElseGet(() -> {
                        ApiError err = new ApiError(ErrorCodes.ACTIVITY_NOT_FOUND, String.format(
                                "Could not find activity with code %s for study with guid %s", activityCode, studyGuid));
                        log.warn(err.getMessage());
                        ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, err);
                        return null;    // Not reached
                    });

            User user = handle.attach(UserDao.class).findUserByGuidOrAltPid(participantGuidOrLegacyAltPid).orElse(null);
            if (user == null) {
                ApiError err = new ApiError(ErrorCodes.USER_NOT_FOUND, String.format(
                        "Could not find participant with guid %s", participantGuidOrLegacyAltPid));
                log.warn(err.getMessage());
                ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, err);
                return;
            }
            String participantGuid = user.getGuid();

            int instanceCount = handle.attach(JdbiActivityInstance.class)
                    .getNumActivitiesForParticipant(activityDto.getActivityId(), user.getId());

            if (activityDto.getMaxInstancesPerUser() != null && instanceCount >= activityDto.getMaxInstancesPerUser()) {
                ApiError err = new ApiError(ErrorCodes.TOO_MANY_INSTANCES, String.format(
                        "Exceeded maximum number of allowed activity instances for participant %s", participantGuid));
                log.warn(err.getMessage());
                ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, err);
                return;
            }

            ActivityInstanceDao instanceDao = handle.attach(ActivityInstanceDao.class);
            if (activityDto.hideExistingInstancesOnCreation()) {
                //hide existing instances
                instanceDao.bulkUpdateIsHiddenByActivityIds(user.getId(), true, Set.of(activityDto.getActivityId()));
            }
            ActivityInstanceDto instanceDto = instanceDao.insertInstance(activityDto.getActivityId(), participantGuid, participantGuid,
                            InstanceStatusType.CREATED, null, Instant.now().toEpochMilli(), payload.getTriggerId(), null);

            if (instanceDto == null || StringUtils.isBlank(instanceDto.getGuid())) {
                ApiError err = new ApiError(ErrorCodes.SERVER_ERROR, String.format(
                        "Unable to create activity instance for participant %s", participantGuid));
                log.error(err.getMessage());
                ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, err);
            } else {
                handle.attach(DataExportDao.class).queueDataSync(user.getId(), studyId);
                log.info("Created on-demand activity instance {} for study guid {}, activity code {}, participant guid {}",
                        instanceDto.getGuid(), studyGuid, activityCode, participantGuid);

                //create answer for this instance if lms/osteo2:SOMATIC_RESULTS
                if ((studyGuid.equalsIgnoreCase("cmi-lms") || studyGuid.equalsIgnoreCase("CMI-OSTEO"))
                        && activityCode.equalsIgnoreCase(RESULT_FILE_ACTIVITY_ID)) {
                    log.info("Populating answer for {} {}", studyGuid, activityCode);
                    String resultsFileName = payload.getResultsFileName();
                    if (StringUtils.isBlank(resultsFileName)) {
                        ApiError err = new ApiError(ErrorCodes.ANSWER_NOT_FOUND, "Invalid results file");
                        ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY, err);
                    }
                    ActivityDefStore activityStore = ActivityDefStore.getInstance();
                    FormActivityDef activityDef = ActivityInstanceUtil.getActivityDef(handle, activityStore, instanceDto, studyGuid);
                    QuestionDef questionDef = activityDef.getQuestionByStableId(RESULT_FILE_STABLE_ID);
                    var answerDao = new AnswerCachedDao(handle);
                    Answer answer = new TextAnswer(null, RESULT_FILE_STABLE_ID, null, resultsFileName, instanceDto.getGuid());
                    String answerGuid = answerDao.createAnswer(user.getId(), instanceDto.getId(), answer, questionDef)
                            .getAnswerGuid();
                    log.info("Created answer with guid {} for {} question stable id {}", answerGuid, studyGuid, RESULT_FILE_STABLE_ID);
                }
            }
        });

        return null;
    }
}
