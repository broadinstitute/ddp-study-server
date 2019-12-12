package org.broadinstitute.ddp.model.event;

import org.apache.commons.lang.NotImplementedException;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;

public class NotificationEventAction extends EventAction {
    private NotificationType notificationType;
    private NotificationServiceType notificationServiceType;
    private long notificationTemplateId;
    private long linkedActivityId;

    public NotificationEventAction(EventConfiguration eventConfiguration, EventConfigurationDto dto) {
        super(eventConfiguration, dto);
        this.notificationType = dto.getNotificationType();
        this.notificationServiceType = dto.getNotificationServiceType();
        this.notificationTemplateId = dto.getNotificationTemplateId();
        this.linkedActivityId = dto.getLinkedActivityId();
    }

    @Override
    public void doAction(PexInterpreter pexInterpreter, Handle handle, EventSignal eventSignal) {
        throw new NotImplementedException("Not implemented until DDP-4294");
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

    public long getLinkedActivityId() {
        return linkedActivityId;
    }
}
