package org.broadinstitute.ddp.model.activity.instance.validation;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.RuleType;

/**
 * A validation rule on composite answers that ensures that all selected options of child answers are unique
 * (no any duplicates).
 */
public class UniqueRule extends Rule<CompositeAnswer> {

    /**
     * Instantiates UniqueRule object with id.
     */
    public static UniqueRule of(Long id, String message, String hint, boolean allowSave) {
        UniqueRule rule = UniqueRule.of(message, hint, allowSave);
        rule.setId(id);
        return rule;
    }

    /**
     * Instantiates UniqueRule object.
     */
    public static UniqueRule of(String message, String hint, boolean allowSave) {
        return new UniqueRule(message, hint, allowSave);
    }

    private UniqueRule(String message, String hint, boolean allowSave) {
        super(RuleType.UNIQUE, message, hint, allowSave);
    }

    @Override
    public boolean validate(Question<CompositeAnswer> question, CompositeAnswer answer) {
        boolean allUnique = true;
        if (answer != null && answer.getValue() != null) {
            Set<String> set = new HashSet<>();
            allUnique = Optional.of(answer).stream()
                    .map(ans -> (CompositeAnswer) ans)
                    .flatMap(parent -> parent.getValue().stream())
                    .flatMap(row -> row.getValues().stream())
                    .allMatch(row -> addAnswerValueToSet(set, row));
        }
        return allUnique;
    }

    private boolean addAnswerValueToSet(Set<String> set, Answer answer) {
        boolean result = true;
        switch (answer.getQuestionType()) {
            case PICKLIST:
                List<SelectedPicklistOption> values = ((PicklistAnswer) answer).getValue();
                for (SelectedPicklistOption option : values) {
                    result &= set.add(option.getDetailText() != null ? option.getDetailText() : option.getStableId());
                }
                break;
            case DATE:
            case AGREEMENT:
            case NUMERIC:
            case TEXT:
            case BOOLEAN:
            case FILE:
                if (answer.getValue() != null) {
                    result &= set.add(answer.getValue().toString());
                }
                break;
            default:
                throw new DDPException("Unhandled answer type " + answer.getQuestionType());
        }
        return result;
    }
}
