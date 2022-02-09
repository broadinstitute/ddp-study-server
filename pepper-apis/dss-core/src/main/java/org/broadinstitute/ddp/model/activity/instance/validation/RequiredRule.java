package org.broadinstitute.ddp.model.activity.instance.validation;

import org.broadinstitute.ddp.model.activity.instance.answer.ActivityInstanceSelectAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.MatrixAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.DateQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.MatrixQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.MatrixGroup;
import org.broadinstitute.ddp.model.activity.instance.question.MatrixRow;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.RuleType;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A validation rule that indicates an answer is required.
 *
 * @param <T> any type of answer
 */
public class RequiredRule<T extends Answer> extends Rule<T> {

    public RequiredRule(Long id, String hint, String message, boolean allowSave) {
        super(RuleType.REQUIRED, message, hint, allowSave);
        setId(id);
    }

    public RequiredRule(String message, String hint, boolean allowSave) {
        super(RuleType.REQUIRED, message, hint, allowSave);
    }

    @Override
    public boolean validate(Question<T> question, T answer) {
        if (answer == null || answer.getValue() == null) {
            return false;
        }

        if (answer.getQuestionType() == QuestionType.PICKLIST) {
            return ((PicklistAnswer) answer).getValue().size() > 0;
        }

        if (answer.getQuestionType() == QuestionType.TEXT) {
            return !((TextAnswer) answer).getValue().isBlank();
        }

        if (answer.getQuestionType() == QuestionType.ACTIVITY_INSTANCE_SELECT) {
            return !((ActivityInstanceSelectAnswer) answer).getValue().isBlank();
        }

        if (answer.getQuestionType() == QuestionType.DATE) {
            return question != null && ((DateQuestion) question).isSpecifiedFieldsPresent((DateAnswer) answer);
        }

        if (answer.getQuestionType() == QuestionType.MATRIX) {
            MatrixQuestion matrixQuestion = (MatrixQuestion) question;
            MatrixAnswer matrixAnswer = (MatrixAnswer) answer;
            Set<String> allCells = new HashSet<>();
            Set<String> groups = matrixQuestion.getGroups().stream().map(MatrixGroup::getStableId).collect(Collectors.toSet());
            Set<String> rows = matrixQuestion.getMatrixQuestionRows().stream().map(MatrixRow::getStableId).collect(Collectors.toSet());
            matrixAnswer.getValue().forEach(cell -> allCells.add(cell.getRowStableId() + ":" + cell.getGroupStableId()));
            for (var row : rows) {
                for (var group : groups) {
                    if (allCells.add(row + ":" + group)) {
                        return false;
                    }
                }
            }
            return true;
        }

        return true;
    }
}
