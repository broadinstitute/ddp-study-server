package org.broadinstitute.ddp.model.event;

import java.time.Instant;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.event.activityinstancecreation.ActivityInstanceCreationEventSyncProcessor;
import org.broadinstitute.ddp.model.event.activityinstancecreation.ActivityInstanceCreationEventSyncProcessorDefault;
import org.broadinstitute.ddp.model.event.activityinstancecreation.ActivityInstanceCreationFromAnswersEventSyncProcessor;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.service.ActivityInstanceCreationService;
import org.jdbi.v3.core.Handle;

@Slf4j
public class ActivityInstanceCreationEventAction extends EventAction {
    private final long studyActivityId;
    private final boolean createFromAnswer;
    private final String sourceQuestionStableId;
    private final String targetQuestionStableId;

    private ActivityInstanceCreationService creationService;

    public ActivityInstanceCreationEventAction(EventConfiguration eventConfiguration, EventConfigurationDto dto) {
        super(eventConfiguration, dto);
        studyActivityId = dto.getActivityInstanceCreationStudyActivityId();
        createFromAnswer = dto.createFromAnswer();
        sourceQuestionStableId = dto.getSourceQuestionStableId();
        targetQuestionStableId = dto.getTargetQuestionStableId();
    }

    public ActivityInstanceCreationEventAction(
            EventConfiguration eventConfiguration,
            long studyActivityId,
            boolean createFromAnswer,
            String sourceQuestionStableId,
            String targetQuestionStableId) {
        super(eventConfiguration, null);
        this.studyActivityId = studyActivityId;
        this.createFromAnswer = createFromAnswer;
        this.sourceQuestionStableId = sourceQuestionStableId;
        this.targetQuestionStableId = targetQuestionStableId;
    }

    @Override
    public void doAction(PexInterpreter interpreter, Handle handle, EventSignal signal) {
        Integer delayBeforePosting = eventConfiguration.getPostDelaySeconds();
        if (delayBeforePosting != null && delayBeforePosting > 0) {
            long postAfter = Instant.now().getEpochSecond() + delayBeforePosting;
            if (!eventConfiguration.dispatchToHousekeeping()) {
                throw new DDPException("ActivityInstance creation with delaySeconds > 0 are currently only supported "
                        + "as asynchronous events. Please set dispatch_to_housekeeping to true");
            } else {
                //insert queued event after checking nested activity and signal
                ActivityDto activityDto = handle.attach(JdbiActivity.class).queryActivityById(studyActivityId);
                getCreationService(signal).checkSignalIfNestedTargetActivity(activityDto.getParentActivityId());
                QueuedEventDao queuedEventDao = handle.attach(QueuedEventDao.class);
                // fixme: serialize signal data such as activityInstanceIdThatChanged, targetStatusType, etc
                // (perhaps leverage templateSubstitutions?) so we can rebuilt the signal and avoid issues
                // when calling doActionSynchronously() in Housekeeping.
                long queuedEventId = queuedEventDao.insertActivityInstanceCreation(eventConfiguration.getEventConfigurationId(),
                        postAfter,
                        signal.getParticipantId(),
                        signal.getOperatorId()
                );
                log.info("Created activity instance queued event: {}", queuedEventId);
            }
        } else {
            doActionSynchronously(handle, signal);
        }
    }

    /**
     * Synchronous processing of ACTIVITY_INSTANCE_CREATION event.
     * The processing of an event delegated to one of implementations of {@link ActivityInstanceCreationEventSyncProcessor}
     * depending on an event parameters.
     */
    public void doActionSynchronously(Handle handle, EventSignal signal) {
        ActivityInstanceCreationEventSyncProcessor activityInstanceCreationEventSyncProcessor;
        if (createFromAnswer) {
            activityInstanceCreationEventSyncProcessor = new ActivityInstanceCreationFromAnswersEventSyncProcessor(
                    handle,
                    signal,
                    studyActivityId,
                    sourceQuestionStableId,
                    targetQuestionStableId,
                    getCreationService(signal));
        } else {
            activityInstanceCreationEventSyncProcessor =
                    new ActivityInstanceCreationEventSyncProcessorDefault(handle, signal, studyActivityId, getCreationService(signal));
        }
        activityInstanceCreationEventSyncProcessor.processInstancesCreation();
    }

    public long getStudyActivityId() {
        return studyActivityId;
    }

    /**
     * Lazily create {@link ActivityInstanceCreationService}
     */
    private ActivityInstanceCreationService getCreationService(EventSignal signal) {
        if (creationService == null) {
            creationService = new ActivityInstanceCreationService(signal);
        }
        return creationService;
    }
}
