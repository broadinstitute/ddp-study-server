package org.broadinstitute.ddp.model.activity.instance.validation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.broadinstitute.ddp.model.activity.instance.answer.NumericAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.NumericQuestion;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class IntRangeRuleTest {

    private static NumericQuestion unused;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        unused = new NumericQuestion("sid", 1L, 2L, false, false, false, null, null, null, List.of(), List.of());
    }

    @Test
    public void testChecksRange() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("range");
        IntRangeRule.of("msg", null, false, 5L, 3L);
    }

    @Test
    public void testValidate_noValue() {
        IntRangeRule rule = IntRangeRule.of("msg", "hint", false, 1L, 10L);
        assertFalse(rule.validate(unused, null));
        assertTrue(rule.validate(unused, new NumericAnswer(1L, "q", "a", null)));
    }

    @Test
    public void testValidate_onlyMinimum() {
        IntRangeRule rule = IntRangeRule.of("msg", "hint", false, 3L, null);
        assertFalse(run(rule, 1));
        assertTrue(run(rule, 3));
        assertTrue(run(rule, 12345));
    }

    @Test
    public void testValidate_onlyMaximum() {
        IntRangeRule rule = IntRangeRule.of("msg", "hint", false, null, 5L);
        assertTrue(run(rule, 1));
        assertTrue(run(rule, 5));
        assertFalse(run(rule, 12345));
    }

    @Test
    public void testValidate_exactly() {
        IntRangeRule rule = IntRangeRule.of("msg", "hint", false, 3L, 3L);
        assertFalse(run(rule, 1));
        assertFalse(run(rule, 12));
        assertTrue(run(rule, 3));
    }

    @Test
    public void testValidate_range() {
        IntRangeRule rule = IntRangeRule.of("msg", "hint", false, 3L, 5L);
        assertFalse(run(rule, 1));
        assertFalse(run(rule, 2));
        assertTrue(run(rule, 3));
        assertTrue(run(rule, 4));
        assertTrue(run(rule, 5));
        assertFalse(run(rule, 12345));
    }

    // Helper to run the rule.
    private boolean run(IntRangeRule rule, long value) {
        return rule.validate(unused, new NumericAnswer(1L, "q", "a", value));
    }
}
