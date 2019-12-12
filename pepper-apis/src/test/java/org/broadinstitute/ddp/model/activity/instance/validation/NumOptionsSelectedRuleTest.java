package org.broadinstitute.ddp.model.activity.instance.validation;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistQuestion;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistSelectMode;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class NumOptionsSelectedRuleTest {

    private static PicklistQuestion unused;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        unused = new PicklistQuestion("sid", 1L, emptyList(), emptyList(), PicklistSelectMode.SINGLE,
                PicklistRenderMode.LIST, 2L, singletonList(new PicklistOption("opt", 3L, null, false, false)));
    }

    @Test
    public void testRule() {
        NumOptionsSelectedRule rule = NumOptionsSelectedRule.of(1L, "msg", "hint", false, 1, 3);
        assertEquals(RuleType.NUM_OPTIONS_SELECTED, rule.getRuleType());
        assertEquals((Long) 1L, rule.getId());
        assertEquals("msg", rule.getDefaultMessage());
        assertEquals("hint", rule.getCorrectionHint());
        assertEquals((Integer) 1, rule.getMin());
        assertEquals((Integer) 3, rule.getMax());
    }

    @Test
    public void testChecksRange_negativeMinimum() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("negative");
        NumOptionsSelectedRule.of("msg", "hint", false, -5, 5);
    }

    @Test
    public void testChecksRange_negativeMaximum() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("negative");
        NumOptionsSelectedRule.of("msg", "hint", false, 5, -5);
    }

    @Test
    public void testChecksRange() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("range");
        NumOptionsSelectedRule.of("msg", "hint", false, 5, 3);
    }

    @Test
    public void testValidate_noValue() {
        NumOptionsSelectedRule rule = NumOptionsSelectedRule.of("msg", "hint", false, 1, 3);
        assertFalse(rule.validate(unused, null));
    }

    @Test
    public void testValidate_onlyMinimum() {
        NumOptionsSelectedRule rule = NumOptionsSelectedRule.of("msg", "hint", false, 2, null);
        assertFalse(run(rule));
        assertFalse(run(rule, "1"));
        assertTrue(run(rule, "1", "2"));
        assertTrue(run(rule, "1", "2", "3"));
    }

    @Test
    public void testValiate_onlyMaximum() {
        NumOptionsSelectedRule rule = NumOptionsSelectedRule.of("msg", "hint", false, null, 2);
        assertTrue(run(rule));
        assertTrue(run(rule, "1"));
        assertTrue(run(rule, "1", "2"));
        assertFalse(run(rule, "1", "2", "3"));
    }

    @Test
    public void testValidate_exactly() {
        NumOptionsSelectedRule rule = NumOptionsSelectedRule.of("msg", "hint", false, 2, 2);
        assertFalse(run(rule));
        assertFalse(run(rule, "1"));
        assertTrue(run(rule, "1", "2"));
        assertFalse(run(rule, "1", "2", "3"));
    }

    @Test
    public void testValidate_range() {
        NumOptionsSelectedRule rule = NumOptionsSelectedRule.of("msg", "hint", false, 1, 2);
        assertFalse(run(rule));
        assertTrue(run(rule, "1"));
        assertTrue(run(rule, "1", "2"));
        assertFalse(run(rule, "1", "2", "3"));
    }

    // Helper to run rule with variable number of selected options.
    private boolean run(NumOptionsSelectedRule rule, String... selected) {
        List<SelectedPicklistOption> options = Arrays.stream(selected)
                .map(SelectedPicklistOption::new)
                .collect(Collectors.toList());
        return rule.validate(unused, new PicklistAnswer(1L, "q", "a", options));
    }
}
