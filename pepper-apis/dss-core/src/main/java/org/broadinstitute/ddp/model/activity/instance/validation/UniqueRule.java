package org.broadinstitute.ddp.model.activity.instance.validation;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

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
 * A validation rule on composite answers that ensures that all child answers are unique
 * (no any duplicates).
 * It verifies that child answers of a {@link CompositeAnswer} have no duplicates.
 * In case if child answers are Picklists where selected values are options (not detailTexts)
 * then verified that there are no repeated stableIds are selected.
 * If Composite contains a SelectedPicklistOption with detailText then such text compared
 * with other detailTexts (not compared with stableIds of selected PicklistOptions).<br>
 * Example of child answers (Picklists) where all selected options/detail texts are unique (the rule returns 'true'):
 * 4 SelectedPicklistOptions refer to PickListOptions with stableIds = {"stId1", "stId2", "stId3", "stId4"}
 * and 2 SelectedPicklistOptions contain detailTexts = {"detail text1", "detail text2"}.<br>
 * Example of child answers (Picklists) where all selected options are not unique (the rule returns 'false'):
 * 4 SelectedPicklistOptions refer to real PickListOptions with stableIds = {"stId1", "stId1", "stId3", "stId4"}<br>
 * (selected 2 options with the same stable ID).
 * Example of child answers (Picklists) where detailTexts are duplicated (the rule returns 'false'):
 * 2 SelectedPicklistOptions contains detailTexts = {"detail text1", "detail text1"}.<br>
 * It could be a case (probably could be called a "corner case") when child answers are of different types
 * (Picklist, Text, Date, etc). In this case checked uniqueness of all values (detailTexts in Picklists, values in
 * answers of other types, but it is not compared with stableIds of selected Picklist options (because
 * it doesn't make sense - it could be possible to compare with picklist options if we compare with option values
 * instead of ids).<br>
 * Example of child answers which contain no duplicates (the rule returns 'true'):
 * 4 SelectedPicklistOptions with stableIds = {"stId1", "stId2", "stId3", "stId4"},
 * 2 SelectedPicklistOptions with detailTexts = {"detail text1", "detail text2"},
 * text answer with value "simple text 1",
 * text answer with value "stId1".
 * Note: even that one of text values equal to one of selected stableIds it is not
 * compared with it therefore it not considered as duplication.
 * Note: it is possible that text answer = "simple text 1" and one of Picklist options = "simple text 1",
 * therefore it duplicated, but this situation is not checked (because in practice in Pepper the Picklists never combined
 * with other types of answers within a Composite).
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
            Set<String> stableIdsSet = new HashSet<>();
            Set<String> valuesSet = new HashSet<>();
            allUnique = Optional.of(answer).stream()
                    .flatMap(parent -> parent.getValue().stream())
                    .flatMap(row -> row.getValues().stream())
                    .allMatch(row -> addAnswerValueToSet(stableIdsSet, row, true) && addAnswerValueToSet(valuesSet, row, false));
        }
        return allUnique;
    }

    private boolean addAnswerValueToSet(Set<String> set, Answer answer, boolean addStableIds) {
        boolean result = true;
        if (answer != null) {
            switch (answer.getQuestionType()) {
                case PICKLIST:
                    List<SelectedPicklistOption> values = ((PicklistAnswer) answer).getValue();
                    for (SelectedPicklistOption option : values) {
                        if (addStableIds) {
                            if (isBlank(option.getDetailText())) {
                                result &= set.add(option.getStableId());
                            }
                        } else {
                            if (isNotBlank(option.getDetailText())) {
                                result &= set.add(option.getDetailText());
                            }
                        }
                    }
                    break;
                case MATRIX:
                case DATE:
                case AGREEMENT:
                case NUMERIC:
                case DECIMAL:
                case TEXT:
                case ACTIVITY_INSTANCE_SELECT:
                case BOOLEAN:
                case FILE:
                    if (!addStableIds && answer.getValue() != null && isNotBlank(answer.getValue().toString())) {
                        result &= set.add(answer.getValue().toString());
                    }
                    break;
                default:
                    throw new DDPException("Unhandled answer type " + answer.getQuestionType());
            }
        }
        return result;
    }
}
