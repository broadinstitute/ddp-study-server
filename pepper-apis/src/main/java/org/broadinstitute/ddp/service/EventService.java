package org.broadinstitute.ddp.service;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfigurationOccurrenceCounter;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.broadinstitute.ddp.model.event.EventSignal;
import org.broadinstitute.ddp.pex.PexException;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventService {

    private static final Logger LOG = LoggerFactory.getLogger(EventService.class);

    private static volatile EventService instance;

    public static EventService getInstance() {
        if (instance == null) {
            synchronized (EventService.class) {
                if (instance == null) {
                    instance = new EventService();
                }
            }
        }
        return instance;
    }

    private void processEventSignalForEventConfiguration(Handle handle,
                                                         EventSignal eventSignal,
                                                         EventConfiguration eventConfig,
                                                         PexInterpreter pexInterpreter) {
        if (eventConfig.isTriggered(handle, eventSignal)) {
            LOG.info(
                    "Checking pre-condition, cancel condition and num occurances of EventConfiguration: "
                            + eventConfig.toString() + " with EventTrigger " + eventConfig.getEventTrigger().toString()
                            + " against EventSignal: " + eventSignal.toString());

            // Checking if the per-user event configuration counter is not exceeded
            int numOccurrences = handle.attach(JdbiEventConfigurationOccurrenceCounter.class)
                    .getOrCreateNumOccurrences(eventConfig.getEventConfigurationId(),
                            eventSignal.getParticipantId());

            LOG.info(
                    "Checking if the occurrences per user ({}) hit the threshold ({}) for event configuration (id = {}), user id = {}",
                    numOccurrences,
                    eventConfig.getMaxOccurrencesPerUser(),
                    eventConfig.getEventConfigurationId(),
                    eventSignal.getParticipantId()
            );

            if (eventConfig.getMaxOccurrencesPerUser() != null && numOccurrences >= eventConfig.getMaxOccurrencesPerUser()) {
                LOG.info(
                        "The number of this event's configuration occurrences for the participant (id = {}) is {}"
                                + " while the allowed maximum number of occurrences per user is {}, skipping the configuration",
                        eventSignal.getParticipantId(), numOccurrences, eventConfig.getMaxOccurrencesPerUser()
                );
                return;
            }

            String cancelExpr = eventConfig.getCancelExpression();
            if (StringUtils.isNotBlank(cancelExpr)) {
                LOG.info("Checking the cancel expression `{}` for the event configuration (id = {})",
                        cancelExpr, eventConfig.getEventConfigurationId());
                try {
                    boolean shouldCancel = pexInterpreter.eval(cancelExpr, handle, eventSignal.getParticipantGuid(),
                            eventSignal.getOperatorGuid(), null, null, eventSignal);
                    if (shouldCancel) {
                        LOG.info("Cancel expression `{}` evaluated to TRUE, skipping the configuration", cancelExpr);
                        return;
                    }
                } catch (PexException e) {
                    throw new DaoException("Error evaluating cancel expression: " + cancelExpr, e);
                }
            }

            // Checking if a precondition expression (if exists) evaluates to TRUE
            String precondExpr = eventConfig.getPreconditionExpression();
            LOG.info(
                    "Checking the precondition expression `{}` for the event configuration (id = {})",
                    eventConfig.getPreconditionExpression(),
                    eventConfig.getEventConfigurationId()
            );
            try {
                if (precondExpr != null && !pexInterpreter.eval(precondExpr, handle,
                        eventSignal.getParticipantGuid(), eventSignal.getOperatorGuid(), null, null, eventSignal)) {
                    LOG.info("Precondition expression {} evaluated to FALSE, skipping the configuration", precondExpr);
                    return;
                }
            } catch (PexException e) {
                throw new DaoException("Error evaluating pex expression " + precondExpr, e);
            }

            eventConfig.doAction(pexInterpreter, handle, eventSignal);

            // Incrementing the counter indicating that the event configuration has been executed
            handle.attach(JdbiEventConfigurationOccurrenceCounter.class).incNumOccurrences(
                    eventConfig.getEventConfigurationId(),
                    eventSignal.getParticipantId()
            );
        }
    }

    public void processAllActionsForEventSignal(Handle handle, EventSignal eventSignal) {
        processSynchronousActionsForEventSignal(handle, eventSignal);
        queueAsynchronousActionsForEventSignal(handle, eventSignal);
    }

    public void processSynchronousActionsForEventSignal(Handle handle, EventSignal eventSignal) {
        // Look up interested EventConfigs:
        List<EventConfiguration> eventConfigs = handle.attach(EventDao.class)
                .getSynchronousEventConfigurationsByStudyIdAndTriggerType(
                        eventSignal.getStudyId(),
                        eventSignal.getEventTriggerType());

        PexInterpreter pexInterpreter = new TreeWalkInterpreter();
        eventConfigs.forEach(eventConfiguration -> processEventSignalForEventConfiguration(handle,
                eventSignal,
                eventConfiguration,
                pexInterpreter));
    }

    public void queueAsynchronousActionsForEventSignal(Handle handle, EventSignal eventSignal) {
        // Look up interested EventConfigs:
        List<EventConfiguration> eventConfigs = handle.attach(EventDao.class)
                .getAsynchronousEventConfigurationsByStudyIdAndTriggerType(
                        eventSignal.getStudyId(),
                        eventSignal.getEventTriggerType());

        PexInterpreter pexInterpreter = new TreeWalkInterpreter();
        eventConfigs.forEach(eventConfiguration -> processEventSignalForEventConfiguration(handle,
                eventSignal,
                eventConfiguration,
                pexInterpreter));
    }
}
