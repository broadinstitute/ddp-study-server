package org.broadinstitute.ddp.model.event;

import java.time.Instant;

import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.InvitationFactory;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.service.EventService;
import org.broadinstitute.ddp.util.MiscUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateInvitationEventAction extends EventAction {

    private static final Logger LOG = LoggerFactory.getLogger(CreateInvitationEventAction.class);

    private String contactEmailQuestionStableId;
    private boolean markExistingAsVoided;

    public CreateInvitationEventAction(EventConfiguration eventConfiguration, EventConfigurationDto dto) {
        this(eventConfiguration, dto.getContactEmailQuestionStableId(), dto.shouldMarkExistingInvitationsAsVoided());
    }

    public CreateInvitationEventAction(EventConfiguration eventConfiguration,
                                       String contactEmailQuestionStableId,
                                       boolean markExistingAsVoided) {
        super(eventConfiguration, null);
        this.contactEmailQuestionStableId = contactEmailQuestionStableId;
        this.markExistingAsVoided = markExistingAsVoided;
    }

    @Override
    public void doAction(PexInterpreter interpreter, Handle handle, EventSignal signal) {
        InvitationCreatedSignal nextSignal = run(handle, signal);
        EventService.getInstance().processAllActionsForEventSignal(handle, nextSignal);
    }

    InvitationCreatedSignal run(Handle handle, EventSignal signal) {
        String contactEmail = fetchContactEmail(handle, signal);
        if (!MiscUtil.isEmailFormatValid(contactEmail)) {
            throw new DDPException("Contact email answer '" + contactEmail + "' is not a valid email");
        }

        if (markExistingAsVoided) {
            Instant now = Instant.now();
            int numVoided = handle.attach(InvitationDao.class)
                    .bulkMarkVoided(signal.getStudyId(), signal.getParticipantId(), now);
            LOG.info("Marked {} existing invitations as voided at {}", numVoided, now);
        }

        InvitationDto invitationDto = handle.attach(InvitationFactory.class)
                .createAgeUpInvitation(signal.getStudyId(), signal.getParticipantId(), contactEmail);
        LOG.info("Created invitation {} for participant {} in study {}",
                invitationDto.getInvitationGuid(), signal.getParticipantGuid(), signal.getStudyId());

        return new InvitationCreatedSignal(
                signal.getOperatorId(),
                signal.getParticipantId(),
                signal.getParticipantGuid(),
                signal.getStudyId(),
                invitationDto);
    }

    private String fetchContactEmail(Handle handle, EventSignal signal) {
        Answer answer;
        AnswerDao answerDao = handle.attach(AnswerDao.class);
        if (signal.getEventTriggerType() == EventTriggerType.ACTIVITY_STATUS) {
            ActivityInstanceStatusChangeSignal statusChangeSignal = (ActivityInstanceStatusChangeSignal) signal;
            long instanceId = statusChangeSignal.getActivityInstanceIdThatChanged();
            LOG.info("Attempting to fetch contact email answer using triggered signal {}", statusChangeSignal);
            answer = answerDao.findAnswerByInstanceIdAndQuestionStableId(
                    instanceId, contactEmailQuestionStableId).orElse(null);
        } else {
            LOG.info("Attempting to fetch latest contact email answer");
            answer = answerDao.findAnswerByLatestInstanceAndQuestionStableId(
                    signal.getParticipantId(), signal.getStudyId(), contactEmailQuestionStableId).orElse(null);
        }

        if (answer == null) {
            throw new DDPException("Could not find answer for contact email question " + contactEmailQuestionStableId);
        }

        LOG.info("Fetched contact email from answer with id {}", answer.getAnswerId());
        return answer.getValue().toString();
    }
}
