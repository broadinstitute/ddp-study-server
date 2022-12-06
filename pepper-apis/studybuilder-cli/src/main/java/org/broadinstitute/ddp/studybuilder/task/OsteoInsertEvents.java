package org.broadinstitute.ddp.studybuilder.task;

import lombok.extern.slf4j.Slf4j;
import one.util.streamex.StreamEx;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.dsm.DsmNotificationEventType;
import org.broadinstitute.ddp.model.event.DsmNotificationTrigger;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.jdbi.v3.core.Handle;

@Slf4j
public class OsteoInsertEvents extends InsertStudyEvents {
    OsteoInsertEvents() {
        super("osteo", "patches/osteo-new-events.conf");
    }

    @Override
    public void run(final Handle handle) {
        removeExistingEvents(handle);
        super.run(handle);
    }

    private void removeExistingEvents(final Handle handle) {
        var eventDao = handle.attach(EventDao.class);
        StreamEx.of(eventDao.getAllEventConfigurationsByStudyId(handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid).getId()))
                .filterBy(EventConfiguration::getEventActionType, EventActionType.ACTIVITY_INSTANCE_CREATION)
                .filterBy(EventConfiguration::getEventTriggerType, EventTriggerType.DSM_NOTIFICATION)
                .filter(this::hasDSMNotificationTrigger)
                .filter(this::isExpectedTrigger)
                .map(EventConfiguration::getEventConfigurationId)
                .forEach(id -> handle.attach(JdbiEventConfiguration.class).updateIsActiveById(id, false));

        log.info("Successfully removed DSM Notification events that create new activities of {}.", studyGuid);
    }

    private boolean hasDSMNotificationTrigger(final EventConfiguration event) {
        return event.getEventTrigger() instanceof DsmNotificationTrigger;
    }

    private boolean isExpectedTrigger(final EventConfiguration event) {
        return isExpectedTrigger(((DsmNotificationTrigger) event.getEventTrigger()).getDsmEventType());
    }

    private boolean isExpectedTrigger(final DsmNotificationEventType eventType) {
        return eventType == DsmNotificationEventType.BLOOD_RECEIVED || eventType == DsmNotificationEventType.SALIVA_RECEIVED;
    }
}
