package org.broadinstitute.ddp.model.event;

import java.time.Instant;

import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.event.activityinstancecreation.ActivityInstanceCreatorDefault;
import org.broadinstitute.ddp.model.event.activityinstancecreation.ActivityInstanceCreatorFromAnswers;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.service.ActivityInstanceCreationService;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActivityInstanceCreationEventAction extends EventAction {

    private static final Logger LOG = LoggerFactory.getLogger(ActivityInstanceCreationEventAction.class);

    private final long studyActivityId;
    private final boolean createFromAnswer;
    private final String sourceQuestionStableId;
    private final String targetQuestionStableId;

    private final ActivityInstanceCreationService creationService;

    public ActivityInstanceCreationEventAction(EventConfiguration eventConfiguration, EventConfigurationDto dto) {
        super(eventConfiguration, dto);
        studyActivityId = dto.getActivityInstanceCreationStudyActivityId();
        createFromAnswer = dto.isCreateFromAnswer() != null ? dto.isCreateFromAnswer() : false;
        sourceQuestionStableId = dto.getSourceQuestionStableId();
        targetQuestionStableId = dto.getTargetQuestionStableId();
        creationService = new ActivityInstanceCreationService();
    }

    public ActivityInstanceCreationEventAction(
            EventConfiguration eventConfiguration,
            long studyActivityId,
            Boolean createFromAnswer,
            String sourceQuestionStableId,
            String targetQuestionStableId) {
        super(eventConfiguration, null);
        this.studyActivityId = studyActivityId;
        this.createFromAnswer = createFromAnswer != null ? createFromAnswer : false;
        this.sourceQuestionStableId = sourceQuestionStableId;
        this.targetQuestionStableId = targetQuestionStableId;
        creationService = new ActivityInstanceCreationService();
    }

    @Override
    public void doAction(PexInterpreter interpreter, Handle handle, EventSignal signal) {
        creationService.setEventSignal(signal);
        Integer delayBeforePosting = eventConfiguration.getPostDelaySeconds();
        if (delayBeforePosting != null && delayBeforePosting > 0) {
            long postAfter = Instant.now().getEpochSecond() + delayBeforePosting;
            if (!eventConfiguration.dispatchToHousekeeping()) {
                throw new DDPException("ActivityInstance creation with delaySeconds > 0 are currently only supported "
                        + "as asynchronous events. Please set dispatch_to_housekeeping to true");
            } else {
                //insert queued event after checking nested activity and signal
                ActivityDto activityDto = handle.attach(JdbiActivity.class).queryActivityById(studyActivityId);
                creationService.checkSignalIfNestedTargetActivity(activityDto);
                QueuedEventDao queuedEventDao = handle.attach(QueuedEventDao.class);
                // fixme: serialize signal data such as activityInstanceIdThatChanged, targetStatusType, etc
                // (perhaps leverage templateSubstitutions?) so we can rebuilt the signal and avoid issues
                // when calling doActionSynchronously() in Housekeeping.
                long queuedEventId = queuedEventDao.insertActivityInstanceCreation(eventConfiguration.getEventConfigurationId(),
                        postAfter,
                        signal.getParticipantId(),
                        signal.getOperatorId()
                );
                LOG.info("Created activity instance queued event: {}", queuedEventId);
            }
        } else {
            doActionSynchronously(handle, signal);
        }
    }

    public void doActionSynchronously(Handle handle, EventSignal signal) {
        ActivityInstanceCreatorDefault activityInstanceCreatorDefault;
        if (createFromAnswer) {
            activityInstanceCreatorDefault = new ActivityInstanceCreatorFromAnswers(
                    studyActivityId,
                    sourceQuestionStableId,
                    targetQuestionStableId,
                    creationService);
        } else {
            activityInstanceCreatorDefault =
                    new ActivityInstanceCreatorDefault(studyActivityId, creationService);
        }
        activityInstanceCreatorDefault.create(handle, signal);
    }

    public long getStudyActivityId() {
        return studyActivityId;
    }
}
