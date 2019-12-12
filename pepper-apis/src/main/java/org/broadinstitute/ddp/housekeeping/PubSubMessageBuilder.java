package org.broadinstitute.ddp.housekeeping;

import static org.broadinstitute.ddp.Housekeeping.DDP_EVENT_CONFIGURATION_ID;
import static org.broadinstitute.ddp.Housekeeping.DDP_EVENT_ID;
import static org.broadinstitute.ddp.Housekeeping.DDP_EVENT_TYPE;
import static org.broadinstitute.ddp.Housekeeping.DDP_HOUSEKEEPING_VERSION;
import static org.broadinstitute.ddp.Housekeeping.DDP_IGNORE_AFTER;
import static org.broadinstitute.ddp.Housekeeping.DDP_MESSAGE_ID;
import static org.broadinstitute.ddp.Housekeeping.DDP_STUDY_GUID;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import com.auth0.exception.Auth0Exception;
import com.auth0.json.mgmt.users.User;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.typesafe.config.Config;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.NotificationTemplateVariables;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.NotificationTemplateSubstitutionDto;
import org.broadinstitute.ddp.db.dto.QueuedEventDto;
import org.broadinstitute.ddp.db.dto.QueuedNotificationDto;
import org.broadinstitute.ddp.db.dto.QueuedPdfGenerationDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.exception.MessageBuilderException;
import org.broadinstitute.ddp.housekeeping.message.NotificationMessage;
import org.broadinstitute.ddp.housekeeping.message.PdfGenerationMessage;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.event.NotificationType;
import org.broadinstitute.ddp.util.Auth0MgmtTokenHelper;
import org.broadinstitute.ddp.util.Auth0Util;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PubSubMessageBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(PubSubMessageBuilder.class);

    private Gson gson;
    private Config cfg;

    public PubSubMessageBuilder(Config cfg) {
        this.gson = GsonUtil.standardGson();
        this.cfg = cfg;
    }

    public PubsubMessage createMessage(String ddpMessageId,
                                       QueuedEventDto pendingEvent,
                                       Handle apisHandle) throws MessageBuilderException {
        if (StringUtils.isBlank(ddpMessageId)) {
            throw new RuntimeException("ddpMessageId is required");
        }
        String messageJson = null;
        if (pendingEvent.getActionType() == EventActionType.NOTIFICATION) {
            QueuedNotificationDto queuedNotificationDto = (QueuedNotificationDto) pendingEvent;
            if (NotificationType.EMAIL == queuedNotificationDto.getNotificationType()
                    || NotificationType.STUDY_EMAIL == queuedNotificationDto.getNotificationType()) {
                Collection<String> sendToList = new HashSet<>();

                // todo techdebt: set email field in QueuedEventDao instead of querying it here. make a list of
                // size one for now.

                if (NotificationType.STUDY_EMAIL == queuedNotificationDto.getNotificationType()) {
                    sendToList.add(queuedNotificationDto.getStudyFromEmail());
                } else if (StringUtils.isNotBlank(queuedNotificationDto.getToEmail())) {
                    // if there's a non-user email address specified, use it
                    sendToList.add(queuedNotificationDto.getToEmail());
                } else {
                    // otherwise, lookup address information for the auth0 account
                    UserDto userDto = apisHandle.attach(JdbiUser.class).findByUserGuid(pendingEvent.getParticipantGuid());

                    User auth0User = null;
                    try {
                        Auth0MgmtTokenHelper auth0MgmtTokenHelper = Auth0Util.getManagementTokenHelperForStudy(
                                apisHandle, pendingEvent.getStudyGuid());

                        auth0User = new Auth0Util(auth0MgmtTokenHelper.getDomain()).getAuth0User(
                                userDto.getAuth0UserId(), auth0MgmtTokenHelper.getManagementApiToken());
                    } catch (Auth0Exception e) {
                        throw new MessageBuilderException("Could not get auth0 user " + userDto.getAuth0UserId(), e);
                    }
                    if (auth0User != null) {
                        if (StringUtils.isNotBlank(auth0User.getEmail())) {
                            sendToList.add(auth0User.getEmail());
                        } else {
                            throw new DDPException("Cannot send email to ddp user " + pendingEvent.getParticipantGuid()
                                    + " because they have no email address");
                        }
                    } else {
                        throw new MessageBuilderException("Cannot send email to ddp user " + pendingEvent.getParticipantGuid()
                                + " because they have no auth0 account");
                    }
                }

                String studyGuid = queuedNotificationDto.getStudyGuid();
                String fromName = queuedNotificationDto.getStudyFromName();
                String fromEmail = queuedNotificationDto.getStudyFromEmail();
                if (NotificationType.STUDY_EMAIL == queuedNotificationDto.getNotificationType()) {
                    fromName = cfg.getString(ConfigFile.Sendgrid.FROM_NAME);
                    fromEmail = cfg.getString(ConfigFile.Sendgrid.FROM_EMAIL);
                }

                // Override the activity instance substitution for email if it has a linked activity.
                boolean shouldSearchForActivityInstance = (queuedNotificationDto.getParticipantGuid() != null
                        && queuedNotificationDto.getLinkedActivityId() != null);

                if (shouldSearchForActivityInstance) {
                    String activityInstanceGuid = apisHandle.attach(JdbiActivityInstance.class)
                            .findLatestInstanceGuidByUserGuidAndActivityId(
                                    queuedNotificationDto.getParticipantGuid(), queuedNotificationDto.getLinkedActivityId())
                            .orElse(null);
                    if (activityInstanceGuid != null) {
                        List<NotificationTemplateSubstitutionDto> filtered = queuedNotificationDto.getTemplateSubstitutions().stream()
                                .filter(dto -> !NotificationTemplateVariables.DDP_ACTIVITY_INSTANCE_GUID.equals(dto.getVariableName()))
                                .collect(Collectors.toList());
                        filtered.add(new NotificationTemplateSubstitutionDto(
                                NotificationTemplateVariables.DDP_ACTIVITY_INSTANCE_GUID, activityInstanceGuid));
                        queuedNotificationDto.getTemplateSubstitutions().clear();
                        queuedNotificationDto.getTemplateSubstitutions().addAll(filtered);
                    } else {
                        LOG.error("Could not find latest activity instance for notification template substitution:"
                                        + " queuedEventId={}, userGuid={}, studyGuid={}, linkedActivityId={}, templateKey={}",
                                queuedNotificationDto.getQueuedEventId(), queuedNotificationDto.getParticipantGuid(), studyGuid,
                                queuedNotificationDto.getLinkedActivityId(), queuedNotificationDto.getTemplateKey());
                    }
                }

                String participantFirstName = queuedNotificationDto.getUserFirstName();
                String participantLastName = queuedNotificationDto.getUserLastName();
                String participantGuid = queuedNotificationDto.getParticipantGuid();
                String participantHruid = queuedNotificationDto.getParticipantHruid();

                queuedNotificationDto.addTemplateSubstitutions(
                        new NotificationTemplateSubstitutionDto(NotificationTemplateVariables.DDP_STUDY_GUID, studyGuid),
                        new NotificationTemplateSubstitutionDto(NotificationTemplateVariables.DDP_PARTICIPANT_GUID, participantGuid),
                        new NotificationTemplateSubstitutionDto(NotificationTemplateVariables.DDP_PARTICIPANT_HRUID, participantHruid));

                NotificationMessage notificationMessage = new NotificationMessage(
                        queuedNotificationDto.getNotificationType(),
                        queuedNotificationDto.getNotificationServiceType(),
                        queuedNotificationDto.getTemplateKey(),
                        sendToList,
                        participantFirstName,
                        participantLastName,
                        participantGuid,
                        studyGuid,
                        fromName,
                        fromEmail,
                        queuedNotificationDto.getApiKey(),
                        queuedNotificationDto.getDefaultSalutation(),
                        queuedNotificationDto.getTemplateSubstitutions(),
                        queuedNotificationDto.getWebBaseUrl(),
                        queuedNotificationDto.getEventConfigurationId()
                );

                messageJson = gson.toJson(notificationMessage);
            } else {
                throw new MessageBuilderException("Unknown notification type "
                        + queuedNotificationDto.getNotificationServiceType()
                        + " for queued event " + pendingEvent.getQueuedEventId());
            }

        } else if (pendingEvent.getActionType() == EventActionType.PDF_GENERATION) {
            QueuedPdfGenerationDto queuedPdfGenerationDto = (QueuedPdfGenerationDto) pendingEvent;
            PdfGenerationMessage pdfGenerationMessage = new PdfGenerationMessage(
                    pendingEvent.getParticipantGuid(),
                    pendingEvent.getStudyGuid(),
                    queuedPdfGenerationDto.getEventConfigurationId(),
                    queuedPdfGenerationDto.getPdfDocumentConfigurationId());
            messageJson = gson.toJson(pdfGenerationMessage);
        } else {
            throw new MessageBuilderException("message has not been determined for queued event " + pendingEvent
                    .getQueuedEventId());
        }
        PubsubMessage.Builder messageBuilder = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(messageJson))
                .putAttributes(DDP_EVENT_ID, pendingEvent.getDdpEventId())
                .putAttributes(DDP_EVENT_TYPE, pendingEvent.getActionType().name())
                .putAttributes(DDP_MESSAGE_ID, ddpMessageId)
                .putAttributes(DDP_EVENT_CONFIGURATION_ID, Long.toString(pendingEvent.getEventConfigurationId()))
                .putAttributes(DDP_STUDY_GUID, pendingEvent.getStudyGuid())
                .putAttributes(DDP_HOUSEKEEPING_VERSION, "1.0");
        if (pendingEvent.getMaxOccurrencesPerUser() != null) {
            messageBuilder.putAttributes(DDP_IGNORE_AFTER, Integer.toString(pendingEvent.getMaxOccurrencesPerUser()));
        }

        messageBuilder.build();
        return messageBuilder.build();
    }
}
