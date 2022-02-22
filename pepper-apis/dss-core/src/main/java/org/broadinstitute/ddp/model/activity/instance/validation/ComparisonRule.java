package org.broadinstitute.ddp.model.activity.instance.validation;

import com.google.common.primitives.Longs;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.ComparisonType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;

@Getter
@SuperBuilder(toBuilder = true)
public class ComparisonRule extends Rule<Answer> {
    @SerializedName("reference_question_id")
    private Long referenceQuestionId;

    @SerializedName("comparison_validation_type")
    private ComparisonType comparisonType;

    private Answer referencedAnswer;

    @Override
    public boolean validate(Question<Answer> question, Answer answer) {
        if (answer.getValue() == null || referencedAnswer.getValue() == null) {
            return true;
        }

        switch (question.getQuestionType()) {
            case NUMERIC:
                return validate(Longs::compare, (Long) referencedAnswer.getValue(), (Long) answer.getValue());
            case DECIMAL:
                return validate(BigDecimal::compareTo, (BigDecimal) referencedAnswer.getValue(), (BigDecimal) answer.getValue());
            case DATE:
                final Optional<LocalDate> referencedDate = ((DateValue) referencedAnswer.getValue()).asLocalDate();
                final Optional<LocalDate> originalDate = ((DateValue) answer.getValue()).asLocalDate();

                return referencedDate.isEmpty() || originalDate.isEmpty()
                        || validate(LocalDate::compareTo, referencedDate.get(), originalDate.get());
            default:
                return false;
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
