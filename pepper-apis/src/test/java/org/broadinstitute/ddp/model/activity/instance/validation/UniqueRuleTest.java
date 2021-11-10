package org.broadinstitute.ddp.model.activity.instance.validation;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.broadinstitute.ddp.model.activity.types.PicklistRenderMode.AUTOCOMPLETE;
import static org.broadinstitute.ddp.model.activity.types.PicklistSelectMode.SINGLE;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.question.CompositeQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistSelectMode;
import org.junit.Test;

/**
 * Tests validation rule {@link UniqueRule}
 */
public class UniqueRuleTest {

    @Test
    public void test_compositeUnique_positive() {
        PicklistQuestion childPicklist = createPicklistQuestion(
                SINGLE, AUTOCOMPLETE, "picklistId", List.of("opt1_Id", "opt2_Id", "opt3_Id"));
        CompositeQuestion parent = createCompositeQuestion("compositeId", List.of(childPicklist));

        CompositeAnswer answer = new CompositeAnswer(null, "compositeId", null);
        answer.addRowOfChildAnswers(new PicklistAnswer(null, "picklistId", null,
                singletonList(new SelectedPicklistOption("opt1_Id"))));
        answer.addRowOfChildAnswers(new PicklistAnswer(null, "picklistId", null,
                singletonList(new SelectedPicklistOption("opt2_Id"))));
        answer.addRowOfChildAnswers(new PicklistAnswer(null, "picklistId", null,
                singletonList(new SelectedPicklistOption("opt3_Id", "detail_text_1"))));
        answer.addRowOfChildAnswers(new PicklistAnswer(null, "picklistId", null,
                singletonList(new SelectedPicklistOption("opt3_Id", "detail_text_2"))));

        assertTrue(UniqueRule.of("msg", "hint", true).validate(parent, answer));
    }

    @Test
    public void test_compositeUnique_negative_sameOptions() {
        PicklistQuestion childPicklist = createPicklistQuestion(
                SINGLE, AUTOCOMPLETE, "picklistId", List.of("opt1_Id", "opt2_Id", "opt3_Id"));
        CompositeQuestion parent = createCompositeQuestion("compositeId", List.of(childPicklist));

        CompositeAnswer answer = new CompositeAnswer(null, "compositeId", null);
        answer.addRowOfChildAnswers(new PicklistAnswer(null, "picklistId", null,
                singletonList(new SelectedPicklistOption("opt1_Id"))));
        answer.addRowOfChildAnswers(new PicklistAnswer(null, "picklistId", null,
                singletonList(new SelectedPicklistOption("opt1_Id"))));
        answer.addRowOfChildAnswers(new PicklistAnswer(null, "picklistId", null,
                singletonList(new SelectedPicklistOption("opt3_Id", "detail_text_1"))));
        answer.addRowOfChildAnswers(new PicklistAnswer(null, "picklistId", null,
                singletonList(new SelectedPicklistOption("opt3_Id", "detail_text_2"))));

        assertFalse(UniqueRule.of("msg", "hint", true).validate(parent, answer));
    }

    @Test
    public void test_compositeUnique_negative_sameDetails() {
        PicklistQuestion childPicklist = createPicklistQuestion(
                SINGLE, AUTOCOMPLETE, "picklistId", List.of("opt1_Id", "opt2_Id", "opt3_Id"));
        CompositeQuestion parent = createCompositeQuestion("compositeId", List.of(childPicklist));

        CompositeAnswer answer = new CompositeAnswer(null, "compositeId", null);
        answer.addRowOfChildAnswers(new PicklistAnswer(null, "picklistId", null,
                singletonList(new SelectedPicklistOption("opt1_Id"))));
        answer.addRowOfChildAnswers(new PicklistAnswer(null, "picklistId", null,
                singletonList(new SelectedPicklistOption("opt2_Id"))));
        answer.addRowOfChildAnswers(new PicklistAnswer(null, "picklistId", null,
                singletonList(new SelectedPicklistOption("opt3_Id", "detail_text_1"))));
        answer.addRowOfChildAnswers(new PicklistAnswer(null, "picklistId", null,
                singletonList(new SelectedPicklistOption("opt3_Id", "detail_text_1"))));

        assertFalse(UniqueRule.of("msg", "hint", true).validate(parent, answer));
    }

    private PicklistQuestion createPicklistQuestion(
            PicklistSelectMode selectMode, PicklistRenderMode renderMode, String pickListStableId, List<String> optionsStableIds) {
        List<PicklistOption> options = new ArrayList<>();
        for (String optionStableId : optionsStableIds) {
            options.add(new PicklistOption(optionStableId, 3L, null, null, false, false, false));
        }
        return new PicklistQuestion(pickListStableId, 1L, emptyList(), emptyList(), selectMode,
                renderMode, 2L, options
        );
    }

    private CompositeQuestion createCompositeQuestion(String questionId, List<Question> childQuestions) {
        return new CompositeQuestion(questionId, 1L, null, false, 2L, 3L, childQuestions, List.of());
    }
}
