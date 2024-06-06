package org.broadinstitute.ddp.studybuilder.task;

import lombok.extern.slf4j.Slf4j;
import one.util.streamex.StreamEx;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.event.DsmNotificationTrigger;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.broadinstitute.ddp.notficationevent.DsmNotificationEventType;
import org.jdbi.v3.core.Handle;

@Slf4j
public class LmsInsertDsmEvents extends InsertStudyEvents {
    public LmsInsertDsmEvents() {
        super("cmi-lms", "patches/lms-dsm-events.conf");
    }

    @Override
    public void run(final Handle handle) {
        removeExistingEvents(handle);
        super.run(handle);
    }

    private void removeExistingEvents(final Handle handle) {
        final var eventDao = handle.attach(EventDao.class);
        final var count = StreamEx.of(eventDao.getAllEventConfigurationsByStudyId(handle.attach(JdbiUmbrellaStudy.class)
                        .findByStudyGuid(studyGuid).getId()))
                .filterBy(EventConfiguration::getEventActionType, EventActionType.ACTIVITY_INSTANCE_CREATION)
                .filterBy(EventConfiguration::getEventTriggerType, EventTriggerType.DSM_NOTIFICATION)
                .filter(this::hasDSMNotificationTrigger)
                .filter(this::isExpectedTrigger)
                .map(EventConfiguration::getEventConfigurationId)
                .map(id -> deactivateEventConfiguration(handle, id))
                .count();

        log.info("Successfully deactivated {} DSM Notification events that create new activities of {}.", count, studyGuid);
    }

    private static int deactivateEventConfiguration(final Handle handle, final Long id) {
        log.info("Event configuration #{} was deactivated", id);
        return handle.attach(JdbiEventConfiguration.class).updateIsActiveById(id, false);
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
