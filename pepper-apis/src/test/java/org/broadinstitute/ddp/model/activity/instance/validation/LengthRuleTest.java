package org.broadinstitute.ddp.model.activity.instance.validation;

import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.TextQuestion;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class LengthRuleTest {

    private static TextQuestion unused;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        unused = new TextQuestion("sid", 1L, 2L, emptyList(), emptyList(), TextInputType.TEXT);
    }

    @Test
    public void testRule() {
        LengthRule rule = LengthRule.of(1L, "msg", "hint", false, 1, 3);
        assertEquals(RuleType.LENGTH, rule.getRuleType());
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
        LengthRule.of("msg", null, false, -5, 5);
    }

    @Test
    public void testChecksRange_negativeMaximum() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("negative");
        LengthRule.of("msg", null, false, 5, -5);
    }

    @Test
    public void testChecksRange() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("range");
        LengthRule.of("msg", null, false, 5, 3);
    }

    @Test
    public void testValidate_noValue() {
        LengthRule rule = LengthRule.of("msg", "hint", false, 0, 10);
        assertFalse(rule.validate(unused, null));
        assertFalse(rule.validate(unused, new TextAnswer(1L, "q", "a", null)));
    }

    @Test
    public void testValidate_onlyMinimum() {
        LengthRule rule = LengthRule.of("msg", "hint", false, 3, null);
        assertEquals(false, run(rule, "1"));
        assertEquals(false, run(rule, "12"));
        assertEquals(true, run(rule, "123"));
        assertEquals(true, run(rule, "1234"));
        assertEquals(true, run(rule, "123456789abcd"));
    }

    @Test
    public void testValidate_onlyMaximum() {
        LengthRule rule = LengthRule.of("msg", "hint", false, null, 5);
        assertEquals(true, run(rule, ""));
        assertEquals(true, run(rule, "123"));
        assertEquals(true, run(rule, "1234"));
        assertEquals(true, run(rule, "12345"));
        assertEquals(false, run(rule, "123456"));
        assertEquals(false, run(rule, "123456789abcd"));
    }

    @Test
    public void testValidate_exactly() {
        LengthRule rule = LengthRule.of("msg", "hint", false, 3, 3);
        assertEquals(false, run(rule, "1"));
        assertEquals(false, run(rule, "12"));
        assertEquals(true, run(rule, "123"));
        assertEquals(false, run(rule, "1234"));
        assertEquals(false, run(rule, "123456789"));
    }

    @Test
    public void testValidate_range() {
        LengthRule rule = LengthRule.of("msg", "hint", false, 3, 5);
        assertEquals(false, run(rule, "1"));
        assertEquals(false, run(rule, "12"));
        assertEquals(true, run(rule, "123"));
        assertEquals(true, run(rule, "1234"));
        assertEquals(true, run(rule, "12345"));
        assertEquals(false, run(rule, "123456"));
        assertEquals(false, run(rule, "1234567"));
    }

    // Helper to run the rule.
    private boolean run(LengthRule rule, String text) {
        return rule.validate(unused, new TextAnswer(1L, "q", "a", text));
    }
}
