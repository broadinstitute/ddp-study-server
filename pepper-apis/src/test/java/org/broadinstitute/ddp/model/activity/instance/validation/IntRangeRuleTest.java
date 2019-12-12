package org.broadinstitute.ddp.model.activity.instance.validation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.broadinstitute.ddp.model.activity.instance.answer.NumericIntegerAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.NumericQuestion;
import org.broadinstitute.ddp.model.activity.types.NumericType;
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
        unused = new NumericQuestion("sid", 1L, 2L, false, false, null, null, List.of(), List.of(), NumericType.INTEGER);
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
        assertTrue(rule.validate(unused, new NumericIntegerAnswer(1L, "q", "a", null)));
    }

    @Test
    public void testValidate_onlyMinimum() {
        IntRangeRule rule = IntRangeRule.of("msg", "hint", false, 3L, null);
        assertEquals(false, run(rule, 1));
        assertEquals(true, run(rule, 3));
        assertEquals(true, run(rule, 12345));
    }

    @Test
    public void testValidate_onlyMaximum() {
        IntRangeRule rule = IntRangeRule.of("msg", "hint", false, null, 5L);
        assertEquals(true, run(rule, 1));
        assertEquals(true, run(rule, 5));
        assertEquals(false, run(rule, 12345));
    }

    @Test
    public void testValidate_exactly() {
        IntRangeRule rule = IntRangeRule.of("msg", "hint", false, 3L, 3L);
        assertEquals(false, run(rule, 1));
        assertEquals(false, run(rule, 12));
        assertEquals(true, run(rule, 3));
    }

    @Test
    public void testValidate_range() {
        IntRangeRule rule = IntRangeRule.of("msg", "hint", false, 3L, 5L);
        assertEquals(false, run(rule, 1));
        assertEquals(false, run(rule, 2));
        assertEquals(true, run(rule, 3));
        assertEquals(true, run(rule, 4));
        assertEquals(true, run(rule, 5));
        assertEquals(false, run(rule, 12345));
    }

    // Helper to run the rule.
    private boolean run(IntRangeRule rule, long value) {
        return rule.validate(unused, new NumericIntegerAnswer(1L, "q", "a", value));
    }
}
