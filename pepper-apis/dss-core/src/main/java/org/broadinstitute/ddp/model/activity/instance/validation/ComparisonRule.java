package org.broadinstitute.ddp.model.activity.instance.validation;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.ComparisonType;
import org.broadinstitute.ddp.model.activity.types.RuleType;

@Getter
public class ComparisonRule extends Rule<Answer> {
    @SerializedName("reference_question_id")
    private Long referenceQuestionId;

    @SerializedName("comparison_validation_type")
    private ComparisonType type;

    public static ComparisonRule of(Long id, String message, String hint,
                                    boolean allowSave, Long referenceQuestionId, ComparisonType type) {
        final ComparisonRule rule = new ComparisonRule(message, hint, allowSave, referenceQuestionId, type);
        rule.setId(id);
        return rule;
    }

    private ComparisonRule(String message, String hint, boolean allowSave, Long referenceQuestionId, ComparisonType type) {
        super(RuleType.COMPARISON, message, hint, allowSave);

        this.referenceQuestionId = referenceQuestionId;
        this.type = type;
    }

    @Override
    public boolean validate(Question<Answer> question, Answer answer) {
        //TODO: Write validation
        return true;
    }
}
