package org.broadinstitute.ddp.model.event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.constants.NotificationTemplateVariables;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiQueuedNotification;
import org.broadinstitute.ddp.db.dao.JdbiQueuedNotificationTemplateSubstitution;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;

@Slf4j
@Getter
public class NotificationEventAction extends EventAction {
    private final NotificationType notificationType;
    private final NotificationServiceType notificationServiceType;
    private final Long linkedActivityId; // Allowed to be null
    private final List<PdfAttachment> pdfAttachments = new ArrayList<>();

    public NotificationEventAction(EventConfiguration eventConfiguration, EventConfigurationDto dto) {
        super(eventConfiguration, dto);
        this.notificationType = dto.getNotificationType();
        this.notificationServiceType = dto.getNotificationServiceType();
        this.linkedActivityId = dto.getLinkedActivityId();
    }

    @Override
    public void doAction(PexInterpreter pexInterpreter, Handle handle, EventSignal eventSignal) {
        if (!eventConfiguration.dispatchToHousekeeping()) {
            throw new DDPException("NotificationEventActions are currently only supported as asynchronous events."
                    + "Please set dispatch_to_housekeeping to true");
        }
        long queuedEventId = run(handle, eventSignal);
        log.info("Inserted queued event {} for configuration {}", queuedEventId,
                eventConfiguration.getEventConfigurationId());
    }

    long run(Handle handle, EventSignal signal) {
        QueuedEventDao queuedEventDao = handle.attach(QueuedEventDao.class);
        Integer delayBeforePosting = eventConfiguration.getPostDelaySeconds();
        if (delayBeforePosting == null) {
            delayBeforePosting = 0;
        }
        long postAfter = Instant.now().getEpochSecond() + delayBeforePosting;

        // FIXME we really shouldn't need to special case this kind of stuff. Lets circle back
        Map<String, String> templateSubstitutions = new HashMap<>();
        if (signal.getEventTriggerType() == EventTriggerType.ACTIVITY_STATUS) {
            JdbiActivityInstance jdbiActivityInstance = handle.attach(JdbiActivityInstance.class);
            ActivityInstanceStatusChangeSignal activityInstanceStatusChangeSignal = (ActivityInstanceStatusChangeSignal) signal;
            templateSubstitutions.put(NotificationTemplateVariables.DDP_ACTIVITY_INSTANCE_GUID, jdbiActivityInstance
                    .getActivityInstanceGuid(activityInstanceStatusChangeSignal.getActivityInstanceIdThatChanged()));
        } else if (signal.getEventTriggerType() == EventTriggerType.LOGIN_ACCOUNT_CREATED) {
            String ticketUrl = ((LoginAccountCreatedSignal) signal).getPasswordResetTicketUrl();
            templateSubstitutions.put(NotificationTemplateVariables.DDP_LINK, ticketUrl);
        }

        long queuedEventId = queuedEventDao.insertNotification(eventConfiguration.getEventConfigurationId(),
                postAfter,
                signal.getParticipantId(),
                signal.getOperatorId(),
                templateSubstitutions);

        if (notificationType == NotificationType.INVITATION_EMAIL) {
            InvitationDto invitationDto;
            if (signal.getEventTriggerType() == EventTriggerType.INVITATION_CREATED) {
                invitationDto = ((InvitationCreatedSignal) signal).getInvitationDto();
                if (invitationDto == null) {
                    throw new DDPException(String.format(
                            "%s event signal for participant %s and study %d does not have invitation context",
                            signal.getEventTriggerType(), signal.getParticipantGuid(), signal.getStudyId()));
                } else {
                    log.info("Received invitation {} from {} event signal for participant {} and study {}",
                            invitationDto.getInvitationGuid(), signal.getEventTriggerType(),
                            signal.getParticipantGuid(), signal.getStudyId());
                }
            } else {
                invitationDto = handle.attach(InvitationDao.class)
                        .findInvitations(signal.getStudyId(), signal.getParticipantId())
                        .stream()
                        .filter(invite -> !invite.isVoid())
                        .max(Comparator.comparing(InvitationDto::getCreatedAt))
                        .orElseThrow(() -> new DDPException(String.format(
                                "Could not find any non-voided invitations for participant %s and study %d",
                                signal.getParticipantGuid(), signal.getStudyId())));
                log.info("Found latest non-voided invitation {} for participant {} and study {}",
                        invitationDto.getInvitationGuid(), signal.getParticipantGuid(), signal.getStudyId());
            }

            int numUpdated = handle.attach(JdbiQueuedNotification.class)
                    .updateEmailAddress(queuedEventId, invitationDto.getContactEmail());
            if (numUpdated != 1) {
                throw new DDPException("Could not override recipient email with invitation contact email");
            }

            handle.attach(JdbiQueuedNotificationTemplateSubstitution.class)
                    .insert(queuedEventId, NotificationTemplateVariables.DDP_INVITATION_ID, invitationDto.getInvitationGuid());
            log.info("Added invitation id {} as email template substitution to queued event id {}",
                    invitationDto.getInvitationGuid(), queuedEventId);
        }

        return queuedEventId;
    }

    public void addPdfAttachment(PdfAttachment pdfAttachment) {
        pdfAttachments.add(pdfAttachment);
    }
}
