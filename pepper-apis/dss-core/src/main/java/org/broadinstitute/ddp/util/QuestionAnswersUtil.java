package org.broadinstitute.ddp.util;

import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;

import java.util.List;

/**
 * Utility class to create answers
 */
public class QuestionAnswersUtil {

    public static String answerNumericQuestion(String pepperQuestionStableId,
                                               String participantGuid,
                                               String instanceGuid,
                                               Long value, AnswerDao answerDao) {
        String guid = null;
        if (value != null) {
            Answer answer = new NumericAnswer(null, pepperQuestionStableId, null, value);
            guid = answerDao.createAnswer(participantGuid, instanceGuid, answer).getAnswerGuid();
        }
        return guid;
    }

    public static String answerBooleanQuestion(String pepperQuestionStableId,
                                               String participantGuid,
                                               String instanceGuid,
                                               Boolean value, AnswerDao answerDao) throws Exception {
        if (value != null) {
            Answer answer = new BoolAnswer(null, pepperQuestionStableId, null, value);
            return answerDao.createAnswer(participantGuid, instanceGuid, answer).getAnswerGuid();
        }
        return null;
    }

    public static String answerAgreementQuestion(String pepperQuestionStableId,
                                                 String participantGuid,
                                                 String instanceGuid,
                                                 Boolean value, AnswerDao answerDao) throws Exception {
        if (value != null) {
            Answer answer = new AgreementAnswer(null, pepperQuestionStableId, null, value);
            return answerDao.createAnswer(participantGuid, instanceGuid, answer).getAnswerGuid();
        }
        return null;
    }

    public static String answerPickListQuestion(String questionStableId, String participantGuid, String instanceGuid,
                                                List<SelectedPicklistOption> selectedPicklistOptions, AnswerDao answerDao) {
        Answer answer = new PicklistAnswer(null, questionStableId, null, selectedPicklistOptions);
        return answerDao.createAnswer(participantGuid, instanceGuid, answer).getAnswerGuid();
    }

    public static String answerDateQuestion(String pepperQuestionStableId, String participantGuid, String instanceGuid,
                                            DateValue value, AnswerDao answerDao) {

        Answer answer = new DateAnswer(null, pepperQuestionStableId, null, null, null, null);
        if (value != null) {
            answer = new DateAnswer(null, pepperQuestionStableId, null,
                    value.getYear(),
                    value.getMonth(),
                    value.getDay());
        }
        return answerDao.createAnswer(participantGuid, instanceGuid, answer).getAnswerGuid();
    }

    public static String answerTextQuestion(String pepperQuestionStableId,
                                            String participantGuid,
                                            String instanceGuid,
                                            String value, AnswerDao answerDao) {
        String guid = null;
        if (value != null) {
            Answer answer = new TextAnswer(null, pepperQuestionStableId, null, value);
            guid = answerDao.createAnswer(participantGuid, instanceGuid, answer).getAnswerGuid();
        }
        return guid;
    }

}
