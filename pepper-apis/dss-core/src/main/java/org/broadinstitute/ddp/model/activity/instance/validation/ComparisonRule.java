package org.broadinstitute.ddp.model.activity.instance.validation;

import com.google.common.primitives.Longs;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.types.DecimalDef;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.ComparisonType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;

@Slf4j
@Getter
@SuperBuilder(toBuilder = true)
public class ComparisonRule extends Rule<Answer> {
    private transient Long referenceQuestionId;

    @SerializedName("reference_question_stable_id")
    private String referenceQuestionStableId;

    @SerializedName("comparison_validation_type")
    private ComparisonType comparisonType;

    @Override
    public boolean validate(Question<Answer> question, Answer answer) {
        if (answer == null) {
            return true;
        }

        if (answer.getValue() == null) {
            return false;
        }

        return TransactionWrapper.withTxn(handle -> {
            final Optional<ActivityInstanceDto> activityInstance = handle.attach(ActivityInstanceDao.class)
                    .findByActivityInstanceGuid(answer.getActivityInstanceGuid());
            if (activityInstance.isEmpty()) {
                log.debug("Activity instance {} doesn't exist", answer.getActivityInstanceGuid());
                return false;
            }

            final Optional<Answer> referencedAnswer = handle.attach(AnswerDao.class)
                    .findAnswerByLatestInstanceAndQuestionId(
                            activityInstance.get().getParticipantId(),
                            activityInstance.get().getStudyId(),
                            referenceQuestionId);
            if (referencedAnswer.isEmpty()) {
                log.debug("Referenced answer is empty activity instance id: {}; question id: {}",
                        answer.getActivityInstanceGuid(), referenceQuestionId);
                return false;
            }

            return validate(question.getQuestionType(), answer, referencedAnswer.get());
        });
    }

    private boolean validate(final QuestionType type, final Answer actualAnswer, final Answer referencedAnswer) {
        if (actualAnswer.getQuestionType() != referencedAnswer.getQuestionType()) {
            throw new RuntimeException(String.format("The answers are incompatible. Actual: %s. Referenced: %s",
                    actualAnswer.getQuestionType(), referencedAnswer.getQuestionType()));
        }

        switch (type) {
            case NUMERIC:
                return validate(Longs::compare, (Long) referencedAnswer.getValue(), (Long) actualAnswer.getValue());
            case DECIMAL:
                return validate(DecimalDef::compareTo, (DecimalDef) referencedAnswer.getValue(), (DecimalDef) actualAnswer.getValue());
            case DATE:
                final Optional<LocalDate> referencedDate = ((DateValue) referencedAnswer.getValue()).asLocalDate();
                final Optional<LocalDate> originalDate = ((DateValue) actualAnswer.getValue()).asLocalDate();

                return referencedDate.isEmpty() || originalDate.isEmpty()
                        || validate(LocalDate::compareTo, referencedDate.get(), originalDate.get());
            default:
                throw new RuntimeException(String.format("The question type is not comparable. Actual type: %s", type));
        }
    }

    private <V> boolean validate(final Comparator<V> comparator, final V referenceValue, final V actualValue) {
        final int comparisonResult = comparator.compare(actualValue, referenceValue);

        switch (comparisonType) {
            case EQUAL:
                return comparisonResult == 0;
            case NOT_EQUAL:
                return comparisonResult != 0;
            case GREATER:
                return comparisonResult > 0;
            case LESS:
                return comparisonResult < 0;
            case GREATER_OR_EQUAL:
                return comparisonResult >= 0;
            case LESS_OR_EQUAL:
                return comparisonResult <= 0;
            default:
                throw new RuntimeException("Unknown comparison type: " + comparisonType);
        }
    }
}
