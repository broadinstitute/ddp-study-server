package org.broadinstitute.ddp.model.activity.instance.validation;

import org.broadinstitute.ddp.model.activity.definition.types.DecimalDef;
import org.broadinstitute.ddp.model.activity.instance.answer.DecimalAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.DecimalQuestion;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DecimalRangeRuleTest {

    private static DecimalQuestion unused;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        unused = new DecimalQuestion("sid", 1L, 2L, false, false, false, null, null, null, List.of(), List.of(), 0);
    }

    @Test
    public void testChecksRange() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("range");
        DecimalRangeRule.of("msg", null, false, BigDecimal.TEN, BigDecimal.ONE);
    }

    @Test
    public void testValidate_noValue() {
        DecimalRangeRule rule = DecimalRangeRule.of("msg", "hint", false, BigDecimal.ZERO, BigDecimal.TEN);
        assertFalse(rule.validate(unused, null));
        assertTrue(rule.validate(unused, new DecimalAnswer(1L, "q", "a", null)));
    }

    @Test
    public void testValidate_onlyMinimum() {
        DecimalRangeRule rule = DecimalRangeRule.of("msg", "hint", false, BigDecimal.ONE, null);
        assertFalse(run(rule, BigDecimal.ZERO));
        assertTrue(run(rule, BigDecimal.ONE));
        assertTrue(run(rule, BigDecimal.TEN));
    }

    @Test
    public void testValidate_onlyMaximum() {
        DecimalRangeRule rule = DecimalRangeRule.of("msg", "hint", false, null, BigDecimal.ONE);
        assertTrue(run(rule, BigDecimal.ZERO));
        assertTrue(run(rule, BigDecimal.ONE));
        assertFalse(run(rule, BigDecimal.TEN));
    }

    @Test
    public void testValidate_exactly() {
        DecimalRangeRule rule = DecimalRangeRule.of("msg", "hint", false, BigDecimal.ONE, BigDecimal.ONE);
        assertFalse(run(rule, BigDecimal.ZERO));
        assertFalse(run(rule, BigDecimal.TEN));
        assertTrue(run(rule, BigDecimal.ONE));
    }

    @Test
    public void testValidate_range() {
        DecimalRangeRule rule = DecimalRangeRule.of("msg", "hint", false, BigDecimal.valueOf(3L), BigDecimal.valueOf(5L));
        assertFalse(run(rule, BigDecimal.ONE));
        assertFalse(run(rule, BigDecimal.valueOf(2L)));
        assertTrue(run(rule, BigDecimal.valueOf(3L)));
        assertTrue(run(rule, BigDecimal.valueOf(4L)));
        assertTrue(run(rule, BigDecimal.valueOf(5L)));
        assertFalse(run(rule, BigDecimal.TEN));
    }

    // Helper to run the rule.
    private boolean run(DecimalRangeRule rule, BigDecimal value) {
        return rule.validate(unused, new DecimalAnswer(1L, "q", "a",
                Optional.ofNullable(value).map(DecimalDef::new).orElse(null)));
    }
}
