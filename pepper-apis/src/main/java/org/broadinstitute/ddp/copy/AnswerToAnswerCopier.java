package org.broadinstitute.ddp.copy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.JdbiCompositeAnswer;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiQuestionCached;
import org.broadinstitute.ddp.db.dto.CompositeQuestionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.AnswerRow;
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
    private final JdbiCompositeAnswer jdbiCompositeAnswer;
    private final JdbiQuestion jdbiQuestion;
    private final AnswerDao answerDao;
    private final Map<Long, CompositeQuestionDto> parentDtosByChildId = new HashMap<>();

    public AnswerToAnswerCopier(Handle handle, long operatorIdForNewAnswers) {
        this.handle = handle;
        this.operatorId = operatorIdForNewAnswers;
        this.jdbiCompositeAnswer = handle.attach(JdbiCompositeAnswer.class);
        this.jdbiQuestion = new JdbiQuestionCached(handle);
        this.answerDao = handle.attach(AnswerDao.class);
    }

    public void copy(FormResponse sourceInstance, QuestionDto sourceQuestion,
                     FormResponse targetInstance, QuestionDto targetQuestion) {
        Answer sourceAnswer = sourceInstance.getAnswer(sourceQuestion.getStableId());
        if (sourceAnswer != null) {
            copySourceAnswer(sourceAnswer, targetInstance, targetQuestion);
        } else {
            Answer parentAnswer = retrieveParentQuestion(sourceQuestion)
                    .map(parentDto -> (CompositeAnswer) sourceInstance.getAnswer(parentDto.getStableId()))
                    .orElse(null);
            if (parentAnswer != null) {
                copyFromParentAnswer((CompositeAnswer) parentAnswer, sourceQuestion, targetInstance, targetQuestion);
            } else {
                LOG.info("No source answer to copy from activity instance {} and question {}",
                        sourceInstance.getGuid(), sourceQuestion.getStableId());
            }
        }
    }

    public Optional<CompositeQuestionDto> retrieveParentQuestion(QuestionDto childQuestion) {
        CompositeQuestionDto parentDto = parentDtosByChildId.get(childQuestion.getId());
        if (parentDto == null) {
            parentDto = jdbiQuestion
                    .findCompositeParentDtoByChildId(childQuestion.getId())
                    .orElse(null);
            if (parentDto != null) {
                parentDtosByChildId.put(childQuestion.getId(), parentDto);
            }
        }
        return Optional.ofNullable(parentDto);
    }

    private void copyFromParentAnswer(CompositeAnswer parentAnswer, QuestionDto sourceQuestion,
                                      FormResponse targetInstance, QuestionDto targetQuestion) {
        QuestionDto targetParentQuestion = retrieveParentQuestion(targetQuestion)
                .orElseThrow(() -> new DDPException(
                        "Could not find parent composite question for target question " + targetQuestion.getStableId()));

        // Find the target composite answer, creating it if it doesn't exist
        Answer result = targetInstance.getAnswer(targetParentQuestion.getStableId());
        if (result == null) {
            CompositeAnswer ans = new CompositeAnswer(null, targetParentQuestion.getStableId(), null);
            result = answerDao.createAnswer(operatorId, targetInstance.getId(), targetParentQuestion.getId(), ans);
            targetInstance.putAnswer(result);
        }
        CompositeAnswer targetParentAnswer = (CompositeAnswer) result;

        // Line up the rows between source and target composite answers
        List<AnswerRow> sourceRows = parentAnswer.getValue();
        List<AnswerRow> targetRows = targetParentAnswer.getValue();
        while (targetRows.size() < sourceRows.size()) {
            targetRows.add(new AnswerRow());
        }

        for (int rowIdx = 0; rowIdx < sourceRows.size(); rowIdx++) {
            // Line up the columns between source and target rows
            List<Answer> sourceRow = sourceRows.get(rowIdx).getValues();
            List<Answer> targetRow = targetRows.get(rowIdx).getValues();
            while (targetRow.size() < sourceRow.size()) {
                targetRow.add(null);
            }

            for (int colIdx = 0; colIdx < sourceRow.size(); colIdx++) {
                Answer sourceChild = sourceRow.get(colIdx);
                if (sourceChild == null || !sourceChild.getQuestionStableId().equals(sourceQuestion.getStableId())) {
                    continue;
                }
                Answer targetChild = targetRow.get(colIdx);
                if (targetChild == null) {
                    targetChild = createTargetAnswer(sourceChild, targetInstance.getId(), targetQuestion);
                    jdbiCompositeAnswer.insertChildAnswerItems(
                            targetParentAnswer.getAnswerId(),
                            List.of(targetChild.getAnswerId()),
                            List.of(rowIdx));
                } else {
                    throw new DDPException("Target answer for question " + targetChild.getQuestionStableId()
                            + " exists and updating is currently not supported");
                }
                targetRow.set(colIdx, targetChild);
            }
        }
    }

    private void copySourceAnswer(Answer sourceAnswer, FormResponse targetInstance, QuestionDto targetQuestion) {
        Answer targetAnswer = targetInstance.getAnswer(targetQuestion.getStableId());
        if (targetAnswer == null) {
            targetAnswer = createTargetAnswer(sourceAnswer, targetInstance.getId(), targetQuestion);
        } else {
            throw new DDPException("Target answer for question " + targetAnswer.getQuestionStableId()
                    + " exists and updating is currently not supported");
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
            throw new DDPException("Copying from top-level composite is not supported");
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
            throw new DDPException("Unhandled copying for answer type " + type);
        }
        return answerDao.createAnswer(operatorId, targetInstanceId, targetQuestion.getId(), targetAnswer);
    }
}
