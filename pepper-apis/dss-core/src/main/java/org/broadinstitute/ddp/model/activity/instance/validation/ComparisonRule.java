package org.broadinstitute.ddp.model.activity.instance.validation;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.ComparisonType;

@Getter
@SuperBuilder(toBuilder = true)
public class ComparisonRule extends Rule<Answer> {
    @SerializedName("reference_question_id")
    private Long referenceQuestionId;

    @SerializedName("comparison_validation_type")
    private ComparisonType comparisonType;

    @Override
    public boolean validate(Question<Answer> question, Answer answer) {
        //TODO: Write validation
        return true;
    }
}
