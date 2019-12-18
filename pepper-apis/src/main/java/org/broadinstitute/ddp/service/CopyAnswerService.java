package org.broadinstitute.ddp.service;

import java.util.List;

import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.AnswerDao;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.event.CopyAnswerValueSetterDefinitions;
import org.broadinstitute.ddp.model.event.ValueSetter;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CopyAnswerService {
    private static Logger LOG = LoggerFactory.getLogger(CopyAnswerService.class);

    private static volatile CopyAnswerService instance;
    private AnswerDao answerDao;

    public static CopyAnswerService getInstance() {
        if (instance == null) {
            synchronized (CopyAnswerService.class) {
                if (instance == null) {
                    instance = new CopyAnswerService();
                }
            }
        }
        return instance;
    }

    private CopyAnswerService() {
        this.answerDao = AnswerDao.fromSqlConfig(ConfigFactory.load(ConfigFile.SQL_CONFIG_FILE));
    }

    public <T> boolean copyAnswerValue(EventConfigurationDto eventConfig, ActivityInstanceDto instanceDto, long operatorId, Handle handle) {
        // this OK. Just sets up the base type
        //noinspection unchecked
        ValueSetter<T> targetValueSetter = (ValueSetter<T>)
                CopyAnswerValueSetterDefinitions.findValueSetter(eventConfig.getCopyAnswerTarget());

        Answer sourceAnswer = getAnswer(instanceDto.getGuid(), eventConfig.getCopySourceQuestionStableId(), handle);

        if (sourceAnswer == null) {
            LOG.info("There was no answer to copy in activity instance: {} and question stable id: {}", instanceDto.getGuid(),
                    eventConfig.getCopySourceQuestionStableId());
            return false;
        }
        T sourceAnswerValue = extractValueFromAnswer(sourceAnswer, targetValueSetter.getValueType());
        return targetValueSetter.setValue(sourceAnswerValue, instanceDto.getParticipantId(), operatorId, handle);
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
                return (T) answer.getValue();
            }
            if (answer instanceof BoolAnswer) {
                //noinspection unchecked
                return (T) answer.getValue().toString();
            }
        } else if (extractionValueType == DateValue.class) {
            if (answer instanceof DateAnswer) {
                //noinspection unchecked
                return (T) answer.getValue();
            }
        }
        throw new DDPException("Currently unable to convert question of class: " + answer.getClass() + " to a value of class: "
                + extractionValueType.getName());
    }
}
