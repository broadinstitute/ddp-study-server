package org.broadinstitute.ddp.copy;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.JdbiCompositeAnswer;
import org.broadinstitute.ddp.db.dao.JdbiCompositeQuestion;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericIntegerAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnswerToAnswerCopier {

    private static final Logger LOG = LoggerFactory.getLogger(AnswerToAnswerCopier.class);

    private final Handle handle;
    private final long operatorId;
    private final JdbiCompositeQuestion jdbiCompositeQuestion;
    private final JdbiCompositeAnswer jdbiCompositeAnswer;
    private final AnswerDao answerDao;

    public AnswerToAnswerCopier(Handle handle, long operatorId) {
        this.handle = handle;
        this.operatorId = operatorId;
        this.jdbiCompositeQuestion = handle.attach(JdbiCompositeQuestion.class);
        this.jdbiCompositeAnswer = handle.attach(JdbiCompositeAnswer.class);
        this.answerDao = handle.attach(AnswerDao.class);
    }

    public void copy(FormResponse sourceInstance, QuestionDto sourceQuestion,
                     FormResponse targetInstance, QuestionDto targetQuestion) {
        Answer sourceAnswer = sourceInstance.getAnswer(sourceQuestion.getStableId());
        if (sourceAnswer == null) {
            Answer parentAnswer = jdbiCompositeQuestion
                    .findParentDtoByChildQuestionId(sourceQuestion.getId())
                    .map(parentDto -> (CompositeAnswer) sourceInstance.getAnswer(parentDto.getStableId()))
                    .orElse(null);
            if (parentAnswer == null) {
                LOG.info("No source answer to copy from activity instance {} and question {}",
                        sourceInstance.getGuid(), sourceQuestion.getStableId());
            } else {
                QuestionDto targetParentQuestion = jdbiCompositeQuestion
                        .findParentDtoByChildQuestionId(targetQuestion.getId())
                        .orElseThrow(() -> new DDPException(
                                "Could not find parent composite question for target question " + targetQuestion.getStableId()));
                copyFromParentAnswer((CompositeAnswer) parentAnswer, sourceQuestion, targetInstance, targetParentQuestion, targetQuestion);
            }
        } else {
            copySourceAnswer(sourceAnswer, targetInstance, targetQuestion);
        }
    }

    private void copyFromParentAnswer(CompositeAnswer parentAnswer, QuestionDto sourceQuestion,
                                      FormResponse targetInstance, QuestionDto targetParentQuestion, QuestionDto targetQuestion) {
        // Find the source child answer in each row
        List<Answer> sourceAnswers = parentAnswer.getValue().stream()
                .map(row -> row.getValues().stream()
                        .filter(child -> sourceQuestion.getStableId().equals(child.getQuestionStableId()))
                        .findFirst()
                        .orElse(null))
                .collect(Collectors.toList());

        // Find the target composite answer, creating it if it doesn't exist
        CompositeAnswer targetParentAnswer = (CompositeAnswer) targetInstance.getAnswer(targetParentQuestion.getStableId());
        if (targetParentAnswer == null) {
            CompositeAnswer ans = new CompositeAnswer(null, targetParentQuestion.getStableId(), null);
            targetParentAnswer = (CompositeAnswer) answerDao
                    .createAnswer(operatorId, targetInstance.getId(), targetParentQuestion.getId(), ans);
        }

        // Find the target child answer in each row
        List<Answer> targetAnswers = targetParentAnswer.getValue().stream()
                .map(row -> row.getValues().stream()
                        .filter(child -> targetQuestion.getStableId().equals(child.getQuestionStableId()))
                        .findFirst()
                        .orElse(null))
                .collect(Collectors.toList());

        // Line up the source and target lists so they have the same number of rows
        targetAnswers = new ArrayList<>(targetAnswers);
        while (targetAnswers.size() < sourceAnswers.size()) {
            targetAnswers.add(null);
        }

        for (int i = 0; i < sourceAnswers.size(); i++) {
            Answer sourceChild = sourceAnswers.get(i);
            if (sourceChild == null) {
                continue;
            }
            Answer targetChild = targetAnswers.get(i);
            if (targetChild == null) {
                targetChild = createTargetAnswer(sourceChild, targetInstance.getId(), targetQuestion);
                jdbiCompositeAnswer.insertChildAnswerItems(
                        targetParentAnswer.getAnswerId(),
                        List.of(targetChild.getAnswerId()),
                        List.of(i));
            } else {
                updateTargetAnswer(sourceChild, targetInstance.getId(), targetChild);
            }
        }
    }

    private void copySourceAnswer(Answer sourceAnswer, FormResponse targetInstance, QuestionDto targetQuestion) {
        Answer targetAnswer = targetInstance.getAnswer(targetQuestion.getStableId());
        if (targetAnswer == null) {
            targetAnswer = createTargetAnswer(sourceAnswer, targetInstance.getId(), targetQuestion);
        } else {
            targetAnswer = updateTargetAnswer(sourceAnswer, targetInstance.getId(), targetAnswer);
        }
        targetInstance.putAnswer(targetAnswer);
    }

    private Answer createTargetAnswer(Answer sourceAnswer, long targetInstanceId, QuestionDto targetQuestion) {
        Answer targetAnswer;
        QuestionType type = sourceAnswer.getQuestionType();
        if (type == QuestionType.AGREEMENT) {
            boolean value = ((AgreementAnswer) sourceAnswer).getValue();
            targetAnswer = new AgreementAnswer(null, targetQuestion.getStableId(), null, value);
        } else if (type == QuestionType.BOOLEAN) {
            boolean value = ((BoolAnswer) sourceAnswer).getValue();
            targetAnswer = new BoolAnswer(null, targetQuestion.getStableId(), null, value);
        } else if (type == QuestionType.COMPOSITE) {
            targetAnswer = null; // todo
        } else if (type == QuestionType.DATE) {
            DateValue value = ((DateAnswer) sourceAnswer).getValue();
            targetAnswer = new DateAnswer(null, targetQuestion.getStableId(), null, value);
        } else if (type == QuestionType.NUMERIC) {
            Long value = ((NumericIntegerAnswer) sourceAnswer).getValue();
            targetAnswer = new NumericIntegerAnswer(null, targetQuestion.getStableId(), null, value);
        } else if (type == QuestionType.PICKLIST) {
            List<SelectedPicklistOption> value = ((PicklistAnswer) sourceAnswer).getValue();
            targetAnswer = new PicklistAnswer(null, targetQuestion.getStableId(), null, value);
        } else if (type == QuestionType.TEXT) {
            String value = ((TextAnswer) sourceAnswer).getValue();
            targetAnswer = new TextAnswer(null, targetQuestion.getStableId(), null, value);
        } else {
            throw new DDPException("Unhandled answer type for copying " + type);
        }
        return answerDao.createAnswer(operatorId, targetInstanceId, targetQuestion.getId(), targetAnswer);
    }

    private Answer updateTargetAnswer(Answer sourAnswer, long targetInstanceId, Answer targetAnswer) {
        throw new DDPException("Target answer for question " + targetAnswer.getQuestionStableId()
                + " exists and updating is currently not supported");
    }
}
