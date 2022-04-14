package org.broadinstitute.ddp.model.activity.instance.validation;

import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.TextQuestion;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class RegexRuleTest {

    private static TextQuestion unused;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        unused = new TextQuestion("sid", 1L, 2L, emptyList(), emptyList(), TextInputType.TEXT);
    }

    @Test
    public void testRule() {
        RegexRule rule = RegexRule.of(1L, "msg", "hint", false, "pattern");
        assertEquals(RuleType.REGEX, rule.getRuleType());
        assertEquals((Long) 1L, rule.getId());
        assertEquals("msg", rule.getDefaultMessage());
        assertEquals("hint", rule.getCorrectionHint());
        assertEquals("pattern", rule.getPattern());
    }

    @Test
    public void testChecksPattern_null() {
        thrown.expect(NullPointerException.class);
        thrown.expectMessage("pattern");
        RegexRule.of("msg", "hint", false, null);
    }

    @Test
    public void testChecksPattern_syntax() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("syntax");
        RegexRule.of("msg", "hint", false, "*");  // The * quantifier needs a preceding element, so not valid.
    }

    @Test
    public void testValidate_noValue() {
        RegexRule rule = RegexRule.of("msg", "hint", false, "pattern");
        assertFalse(rule.validate(unused, null));
        assertFalse(rule.validate(unused, new TextAnswer(1L, "q", "a", null)));
    }

    @Test
    public void testValidate_exactMatch() {
        RegexRule rule = RegexRule.of("msg", "hint", false, "exact");
        assertFalse(rule.validate(unused, new TextAnswer(1L, "q", "a", "something else")));
        assertTrue(rule.validate(unused, new TextAnswer(1L, "q", "a", "exact")));
    }

    @Test
    public void testValidate_patternMatch() {
        String exactlyTwoWords = "^\\w+\\s\\w+$";
        RegexRule rule = RegexRule.of("msg", "hint", false, exactlyTwoWords);
        assertFalse(rule.validate(unused, new TextAnswer(1L, "q", "a", "hello")));
        assertFalse(rule.validate(unused, new TextAnswer(1L, "q", "a", "  hello world   ")));
        assertTrue(rule.validate(unused, new TextAnswer(1L, "q", "a", "hello world")));
    }
}
