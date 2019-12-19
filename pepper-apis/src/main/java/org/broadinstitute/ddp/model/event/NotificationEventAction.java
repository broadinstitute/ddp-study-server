package org.broadinstitute.ddp.model.event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.constants.NotificationTemplateVariables;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotificationEventAction extends EventAction {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationEventAction.class);

    private NotificationType notificationType;
    private NotificationServiceType notificationServiceType;
    private long notificationTemplateId;
    private Long linkedActivityId; // Allowed to be null
    private List<PdfAttachment> pdfAttachmentList = new ArrayList<>();

    public NotificationEventAction(EventConfiguration eventConfiguration, EventConfigurationDto dto) {
        super(eventConfiguration, dto);
        this.notificationType = dto.getNotificationType();
        this.notificationServiceType = dto.getNotificationServiceType();
        this.notificationTemplateId = dto.getNotificationTemplateId();
        this.linkedActivityId = dto.getLinkedActivityId();
    }

    public void addPdfAttachment(PdfAttachment pdfAttachment) {
        pdfAttachmentList.add(pdfAttachment);
    }

    public List<PdfAttachment> getPdfAttachmentList() {
        return pdfAttachmentList;
    }

    @Override
    public void doAction(PexInterpreter pexInterpreter, Handle handle, EventSignal eventSignal) {
        if (!eventConfiguration.dispatchToHousekeeping()) {
            throw new DDPException("NotificationEventActions are currently only supported as asynchronous events."
                    + "Please set dispatch_to_housekeeping to true");
        }
        QueuedEventDao queuedEventDao = handle.attach(QueuedEventDao.class);
        Integer delayBeforePosting = eventConfiguration.getPostDelaySeconds();
        if (delayBeforePosting == null) {
            delayBeforePosting = 0;
        }
        long postAfter = Instant.now().getEpochSecond() + delayBeforePosting;

        // FIXME we really shouldn't need to special case this kind of stuff. Lets circle back
        Map<String, String> templateSubstitutions = new HashMap<>();
        if (eventSignal.getEventTriggerType() == EventTriggerType.ACTIVITY_STATUS) {
            JdbiActivityInstance jdbiActivityInstance = handle.attach(JdbiActivityInstance.class);
            ActivityInstanceStatusChangeSignal activityInstanceStatusChangeSignal = (ActivityInstanceStatusChangeSignal) eventSignal;
            templateSubstitutions.put(NotificationTemplateVariables.DDP_ACTIVITY_INSTANCE_GUID, jdbiActivityInstance
                    .getActivityInstanceGuid(activityInstanceStatusChangeSignal.getActivityInstanceIdThatChanged()));
        }

        Long queuedEventId = queuedEventDao.insertNotification(eventConfiguration.getEventConfigurationId(),
                postAfter,
                eventSignal.getParticipantId(),
                eventSignal.getOperatorId(),
                templateSubstitutions);

        LOG.info("Inserted queued event {} for configuration {}", queuedEventId,
                eventConfiguration.getEventConfigurationId());
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public NotificationServiceType getNotificationServiceType() {
        return notificationServiceType;
    }

    public long getNotificationTemplateId() {
        return notificationTemplateId;
    }

    public Long getLinkedActivityId() {
        return linkedActivityId;
    }
}
