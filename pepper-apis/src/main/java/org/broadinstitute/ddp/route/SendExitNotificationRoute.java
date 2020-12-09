package org.broadinstitute.ddp.route;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.NotificationTemplateVariables;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dao.StudyDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.json.study.StudyExitRequestPayload;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.study.StudyExitRequest;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class SendExitNotificationRoute extends ValidatedJsonInputRoute<StudyExitRequestPayload> {

    private static final Logger LOG = LoggerFactory.getLogger(SendExitNotificationRoute.class);

    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_BAD_REQUEST;
    }

    @Override
    public Object handle(Request request, Response response, StudyExitRequestPayload payload) {
        String operatorGuid = RouteUtil.getDDPAuth(request).getOperator();
        String userGuid = request.params(RouteConstants.PathParam.USER_GUID);
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);

        LOG.info("Attempting to create study exit request for user={}, operator={}, study={}", userGuid, operatorGuid, studyGuid);

        return TransactionWrapper.withTxn(handle -> {
            UserDto userDto = handle.attach(JdbiUser.class).findByUserGuid(userGuid);
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);

            EnrollmentStatusType status = handle.attach(JdbiUserStudyEnrollment.class)
                    .getEnrollmentStatusByUserAndStudyGuids(userGuid, studyGuid)
                    .orElseThrow(() -> {
                        String msg = String.format("User %s is not in study %s", userGuid, studyGuid);
                        ApiError err = new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED, msg);
                        LOG.warn(msg);
                        return ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY, err);
                    });

            if (status.isExited()) {
                String msg = String.format("User %s in study %s is already exited", userGuid, studyGuid);
                ApiError err = new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED, msg);
                LOG.warn(msg);
                throw ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY, err);
            }

            StudyDao studyDao = handle.attach(StudyDao.class);
            boolean alreadyRequested = studyDao.findExitRequestForUser(userDto.getUserId()).isPresent();
            if (alreadyRequested) {
                String msg = String.format("User %s has already made exit request in study %s", userGuid, studyGuid);
                ApiError err = new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED, msg);
                LOG.warn(msg);
                throw ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY, err);
            }

            List<EventConfigurationDto> eventDtos = handle.attach(EventDao.class)
                    .getEventConfigurationDtosForStudyIdAndTriggerType(studyDto.getId(), EventTriggerType.EXIT_REQUEST);

            if (eventDtos.isEmpty()) {
                String msg = String.format("Study %s does not support exit requests", studyGuid);
                ApiError err = new ApiError(ErrorCodes.NOT_SUPPORTED, msg);
                LOG.warn(msg);
                throw ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY, err);
            }

            User operatorUser = handle.attach(UserDao.class).findUserByGuid(operatorGuid)
                    .orElseThrow(() -> new DDPException("Could not find operator user with guid " + operatorGuid));
            QueuedEventDao queuedEventDao = handle.attach(QueuedEventDao.class);
            for (EventConfigurationDto eventDto : eventDtos) {
                if (eventDto.getEventActionType() == EventActionType.NOTIFICATION) {
                    Map<String, String> vars = new HashMap<>();
                    vars.put(NotificationTemplateVariables.DDP_PARTICIPANT_EXIT_NOTES, StringUtils.defaultString(payload.getNotes()));
                    queuedEventDao.insertNotification(eventDto.getEventConfigurationId(), 0L,
                            userDto.getUserId(), operatorUser.getId(), vars, null);
                } else {
                    LOG.error("Exit request event configuration with id={} is configured with unsupported actionType={}",
                            eventDto.getEventConfigurationId(), eventDto.getEventActionType());
                }
            }

            long requestId = studyDao.insertExitRequest(new StudyExitRequest(studyDto.getId(), userDto.getUserId(), payload.getNotes()));
            LOG.info("Created study exit request with id={} for user={}, study={}", requestId, userGuid, studyGuid);

            response.status(HttpStatus.SC_NO_CONTENT);
            return "";
        });
    }
}
