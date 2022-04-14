package org.broadinstitute.ddp.route;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.auth0.exception.Auth0Exception;
import com.auth0.json.mgmt.users.User;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.NotificationTemplateVariables;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dao.WorkflowDao;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.SendEmailPayload;
import org.broadinstitute.ddp.json.workflow.WorkflowActivityResponse;
import org.broadinstitute.ddp.json.workflow.WorkflowResponse;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.workflow.StaticState;
import org.broadinstitute.ddp.model.workflow.WorkflowState;
import org.broadinstitute.ddp.service.WorkflowService;
import org.broadinstitute.ddp.util.Auth0Util;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class SendEmailRoute extends ValidatedJsonInputRoute<SendEmailPayload> {

    private static final Logger LOG = LoggerFactory.getLogger(SendEmailRoute.class);

    private final WorkflowService workflowService;


    public SendEmailRoute(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Override
    public Object handle(Request request, Response response, SendEmailPayload payload) throws Exception {
        String email = payload.getEmail();
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);

        TransactionWrapper.useTxn(handle -> {
            LOG.info("Handling email resend for {} in study {}", email, studyGuid);
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
            var mgmtClient = Auth0ManagementClient.forStudy(handle, studyGuid);
            List<User> auth0Users;

            Auth0Util auth0Util = new Auth0Util(mgmtClient.getDomain());
            try {
                auth0Users = auth0Util.getAuth0UsersByEmail(email, mgmtClient.getToken());
            } catch (Auth0Exception e) {
                throw new DDPException("Error while looking up auth0 user " + email, e);
            }

            if (auth0Users.isEmpty()) {
                LOG.info("Attempt to send an email to nonexistent user " + email);
            }

            boolean foundUser = false;

            // it's possible that there are multiple auth0 accounts associated with a given email,
            // for example using non-social and social logins with the same email address
            for (User auth0User : auth0Users) {
                String auth0UserId = auth0User.getId();
                UserDto userDto = handle.attach(JdbiUser.class).findByAuth0UserId(auth0UserId, studyDto.getAuth0TenantId());
                // getting null sometimes: non-registered auth0 user?
                if (userDto == null) {
                    continue;
                } else {
                    foundUser = true;
                }
                String participantGuid = userDto.getUserGuid();
                // verify that returned state is an activity state or done state
                String operatorGuid = participantGuid;
                Optional<WorkflowState> nextState = workflowService.suggestNextState(
                        handle,
                        operatorGuid,
                        participantGuid,
                        studyGuid,
                        StaticState.returningUser()
                );
                if (nextState.isPresent()) {
                    WorkflowResponse workflowResponse = workflowService.buildStateResponse(handle, participantGuid,
                            nextState.get());
                    WorkflowDao workflowDao = handle.attach(WorkflowDao.class);

                    Long workflowStateId = workflowDao.findWorkflowStateId(nextState.get()).orElse(null);

                    if (workflowStateId == null) {
                        throw new DaoException("Could not find workflow state id for " + nextState.get().getType());
                    }

                    String activityInstanceGuid = null;

                    if (workflowResponse instanceof WorkflowActivityResponse) {
                        activityInstanceGuid = ((WorkflowActivityResponse) workflowResponse).getInstanceGuid();
                    }

                    EventDao eventDao = handle.attach(EventDao.class);
                    QueuedEventDao queuedEventDao = handle.attach(QueuedEventDao.class);

                    List<EventConfigurationDto> eventConfigs = eventDao.getNotificationConfigsForWorkflowState(
                            studyGuid,
                            workflowStateId);

                    if (eventConfigs.isEmpty()) {
                        LOG.info("{} event configurations for workflow state {} in study {}.  No email will be "
                                        + "sent.",
                                eventConfigs.size(),
                                workflowStateId,
                                studyGuid);
                    }

                    Map<String, String> templateSubstitutions = new HashMap<>();
                    // if we have a next activity instance guid, add it to the template substitutions
                    if (StringUtils.isNotBlank(activityInstanceGuid)) {
                        templateSubstitutions.put(NotificationTemplateVariables.DDP_ACTIVITY_INSTANCE_GUID,
                                activityInstanceGuid);
                    }

                    // queue up the notifications
                    for (EventConfigurationDto eventConfig : eventConfigs) {
                        long queuedEventId = queuedEventDao.insertNotification(eventConfig
                                        .getEventConfigurationId(),
                                0L,
                                userDto.getUserId(),
                                userDto.getUserId(),
                                templateSubstitutions);

                        LOG.info("Queued queuedEventId {} for email resend to participant {}", queuedEventId,
                                userDto.getUserGuid());
                    }

                } else {
                    LOG.info("No applicable workflow states for email resend to {} in study {}.  No email sent.",
                            email, studyGuid);
                }
            }
            if (!foundUser) {
                List<EventConfigurationDto> eventConfigs =
                        handle.attach(EventDao.class)
                                .getNotificationConfigsForMailingListByEventType(studyGuid, EventTriggerType.USER_NOT_IN_STUDY);
                if (eventConfigs.isEmpty()) {
                    LOG.info("{} event configurations for unregistered user with email {} in study {}."
                                    + "  No email will be sent.",
                            eventConfigs.size(),
                            email,
                            studyGuid);
                }

                Map<String, String> templateSubstitutions = new HashMap<>();
                templateSubstitutions.put(NotificationTemplateVariables.DDP_PARTICIPANT_FROM_EMAIL, email);

                for (EventConfigurationDto eventConfig : eventConfigs) {
                    long queuedEventId = handle.attach(QueuedEventDao.class).insertNotification(eventConfig.getEventConfigurationId(),
                            0,
                            email,
                            templateSubstitutions);
                    LOG.info("Queued queuedEventId {} for email resend to participant not in study with email address {}",
                            queuedEventId,
                            email);
                }
            }
        });
        // todo arz fixme
        return "";
    }
}
