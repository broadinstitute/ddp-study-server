package org.broadinstitute.ddp.model.event;

import java.util.List;

import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.AnswerDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CopyAnswerEventAction extends EventAction {
    private static final Logger LOG = LoggerFactory.getLogger(CopyAnswerEventAction.class);
    private CopyAnswerTarget copyAnswerTarget;
    private String copySourceQuestionStableId;
    private AnswerDao answerDao;

    public CopyAnswerEventAction(EventConfiguration eventConfiguration, EventConfigurationDto dto) {
        super(eventConfiguration, dto);
        this.copyAnswerTarget = dto.getCopyAnswerTarget();
        copySourceQuestionStableId = dto.getCopySourceQuestionStableId();
        answerDao = AnswerDao.fromSqlConfig(ConfigFactory.load(ConfigFile.SQL_CONFIG_FILE));
    }

    @Override
    public void doAction(PexInterpreter pexInterpreter, Handle handle, EventSignal eventSignal) {
        // FIXME The Copy Answer Action was originally written to assume that the activity that triggered the copy
        // FIXME is the source. This means we have to assume that the trigger for this action WAS an activity changing.
        // FIXME Fix so that CopyEventAction is defined in terms of source and destination. OR: change answerdao not to
        // FIXME need the activity ID, since question stable is guaranteed to be unique.

        JdbiActivityInstance jdbiActivityInstance = handle.attach(JdbiActivityInstance.class);
        if (eventSignal.getEventTriggerType() != EventTriggerType.ACTIVITY_STATUS) {
            throw new DDPException("Currently the copy answer action requires the trigger of the action be activity status");
        }
        ActivityInstanceStatusChangeSignal activityInstanceStatusChangeSignal =
                (ActivityInstanceStatusChangeSignal) eventSignal;
        String activityInstanceGuid = jdbiActivityInstance
                .getActivityInstanceGuid(activityInstanceStatusChangeSignal.getActivityInstanceIdThatChanged());


        copyAnswerValue(eventSignal, activityInstanceGuid, handle);
    }

    public <T> boolean copyAnswerValue(EventSignal eventSignal, String activityInstanceGuid, Handle handle) {
        // this OK. Just sets up the base type
        //noinspection unchecked
        ValueSetter<T> targetValueSetter = (ValueSetter<T>)
                CopyAnswerValueSetterDefinitions.findValueSetter(copyAnswerTarget);

        Answer sourceAnswer = getAnswer(activityInstanceGuid, activityInstanceGuid, handle);

        if (sourceAnswer == null) {
            LOG.info("There was no answer to copy in activity instance: {} and question stable id: {}", activityInstanceGuid,
                    copySourceQuestionStableId);
            return false;
        }
        T sourceAnswerValue = extractValueFromAnswer(sourceAnswer, targetValueSetter.getValueType());
        return targetValueSetter.setValue(sourceAnswerValue, eventSignal.getParticipantId(),
                eventSignal.getOperatorId(), handle);
    }

    private Answer getAnswer(String activityInstanceGuid, String questionStableId, Handle handle) {
        //todo refactor answerdao.getAnswersForQuestion . Don't think languageid is used at all
        Long engId = handle.attach(JdbiLanguageCode.class).getLanguageCodeId("en");
        List<Answer> answer = answerDao.getAnswersForQuestion(handle, activityInstanceGuid, questionStableId, engId);
        if (answer.size() == 1) {
            return answer.get(0);
        } else if (answer.size() == 0) {
            return null;
        } else {
            throw new DDPException("Was expecting only 1 answer but found " + answer.size());
        }
    }

    private <T> T extractValueFromAnswer(Answer answer, Class<T> extractionValueType) {
        if (extractionValueType == String.class) {
            if (answer instanceof TextAnswer) {
                //we are checking despite what the warning says
                //noinspection unchecked
                return (T) ((TextAnswer) answer).getValue();
            }
            if (answer instanceof BoolAnswer) {
                //noinspection unchecked
                return (T) answer.getValue().toString();
            }
        }
        throw new DDPException("Currently unable to convert question of class: " + answer.getClass() + " to a value of class: "
                + extractionValueType.getName());
    }

    public CopyAnswerTarget getCopyAnswerTarget() {
        return copyAnswerTarget;
    }

    public String getCopySourceQuestionStableId() {
        return copySourceQuestionStableId;
    }
}
