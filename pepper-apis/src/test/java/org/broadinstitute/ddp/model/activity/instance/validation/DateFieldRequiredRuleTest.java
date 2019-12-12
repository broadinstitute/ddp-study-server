package org.broadinstitute.ddp.model.activity.instance.validation;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.broadinstitute.ddp.model.activity.types.DateFieldType.YEAR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.DateQuestion;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class DateFieldRequiredRuleTest {

    private static DateQuestion unused;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        unused = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, singletonList(YEAR));
    }

    @Test
    public void testRule() {
        DateFieldRequiredRule rule = DateFieldRequiredRule.of(
                RuleType.DAY_REQUIRED, 1L, "msg", "hint", false);
        assertEquals(RuleType.DAY_REQUIRED, rule.getRuleType());
        assertEquals((Long) 1L, rule.getId());
        assertEquals("msg", rule.getDefaultMessage());
        assertEquals("hint", rule.getCorrectionHint());
    }

    @Test
    public void testChecksRuleType() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("rule type");
        DateFieldRequiredRule.of(RuleType.REQUIRED, "msg", "hint", false);
    }

    @Test
    public void testValidate_noValue() {
        DateFieldRequiredRule rule = DateFieldRequiredRule.of(RuleType.DAY_REQUIRED, "msg", "hint", false);
        assertFalse(rule.validate(unused, null));
    }

    @Test
    public void testValidate_dayRequired() {
        DateFieldRequiredRule rule = DateFieldRequiredRule.of(RuleType.DAY_REQUIRED, "msg", "hint", false);
        assertFalse(rule.validate(unused, new DateAnswer(null, "q", "a", 2018, 3, null)));
        assertTrue(rule.validate(unused, new DateAnswer(null, "q", "a", 2018, 3, 14)));
    }

    @Test
    public void testValidate_monthRequired() {
        DateFieldRequiredRule rule = DateFieldRequiredRule.of(
                RuleType.MONTH_REQUIRED, "msg", "hint", false);
        assertFalse(rule.validate(unused, new DateAnswer(null, "q", "a", 2018, null, 14)));
        assertTrue(rule.validate(unused, new DateAnswer(null, "q", "a", 2018, 3, 14)));
    }

    @Test
    public void testValidate_yearRequired() {
        DateFieldRequiredRule rule = DateFieldRequiredRule.of(
                RuleType.YEAR_REQUIRED, "msg", "hint", false);
        assertFalse(rule.validate(unused, new DateAnswer(null, "q", "a", null, 3, 14)));
        assertTrue(rule.validate(unused, new DateAnswer(null, "q", "a", 2018, 3, 14)));
    }
}
