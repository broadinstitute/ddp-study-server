package org.broadinstitute.ddp.model.activity.instance.validation;

import static java.util.Collections.emptyList;
import static junit.framework.TestCase.assertTrue;
import static org.broadinstitute.ddp.model.activity.types.DateFieldType.DAY;
import static org.broadinstitute.ddp.model.activity.types.DateFieldType.MONTH;
import static org.broadinstitute.ddp.model.activity.types.DateFieldType.YEAR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.time.LocalDate;
import java.time.Month;
import java.util.Arrays;
import java.util.List;

import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.DateQuestion;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.junit.Test;

public class AgeRangeRuleTest {
    @Test
    public void testRule() {
        AgeRangeRule rule = AgeRangeRule.of(1L, "msg", "hint", false, 6, 8);
        assertEquals(RuleType.AGE_RANGE, rule.getRuleType());
        assertEquals((Long) 1L, rule.getId());
        assertEquals("msg", rule.getDefaultMessage());
        assertEquals("hint", rule.getCorrectionHint());
        assertEquals(6L, (long)rule.getMinAge());
        assertEquals(8L, (long)rule.getMaxAge());
    }

    @Test
    public void testValidate_noValue() {
        List<DateFieldType> fields = Arrays.asList(YEAR, MONTH, DAY);
        DateQuestion question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        AgeRangeRule rule = AgeRangeRule.of("msg", "hint", false, 0, 18);
        assertFalse(rule.validate(question, null));
    }

    @Test
    public void testValidate_emptyValue() {
        List<DateFieldType> fields = Arrays.asList(YEAR, MONTH, DAY);
        DateQuestion question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        AgeRangeRule rule = AgeRangeRule.of("msg", "hint", false, 2, 4);
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", null, null, null)));
    }

    @Test
    public void testValidate_noMinOrMax() {
        List<DateFieldType> fields = Arrays.asList(YEAR, MONTH, DAY);
        DateQuestion question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        AgeRangeRule rule = AgeRangeRule.of("msg", "hint", false, null, null);
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 1988, 1, 1)));
    }

    @Test
    public void testValidate_MinAge() {
        List<DateFieldType> fields = Arrays.asList(YEAR, MONTH, DAY);
        DateQuestion question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        AgeRangeRule rule = AgeRangeRule.of("msg", "hint", false, 5, null);
        rule.setToday(LocalDate.of(2019, Month.AUGUST, 5));
        //the future
        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", 2032, 1, 13)));
        //today
        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", 2019, 1, 13)));
        // made it!
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2014, 8, 5)));

        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2001, 8, 5)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 1998, 3, 15)));

        rule.setToday(LocalDate.of(2021, Month.FEBRUARY, 28));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2011, 2, 28)));
        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", 2016, 2, 29)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2011, 3, 1)));
    }

    @Test
    public void testValidate_MaxAge() {
        List<DateFieldType> fields = Arrays.asList(YEAR, MONTH, DAY);
        DateQuestion question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        AgeRangeRule rule = AgeRangeRule.of("msg", "hint", false, null, 18);
        rule.setToday(LocalDate.of(2019, Month.AUGUST, 5));

        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", 1988, 3, 13)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2001, 8, 5)));

        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", 1999, 8, 5)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2001, 8, 4)));

        rule.setToday(LocalDate.of(2020, Month.FEBRUARY, 29));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2002, 2, 28)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2002, 3, 1)));

        rule = AgeRangeRule.of("msg", "hint", false, null, 5);
        rule.setToday(LocalDate.of(2021, Month.FEBRUARY, 28));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2016, 2, 29)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2016, 3, 1)));
    }

    @Test
    public void testValidate_withinRange() {
        List<DateFieldType> fields = Arrays.asList(YEAR, MONTH, DAY);
        DateQuestion question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        AgeRangeRule rule = AgeRangeRule.of("msg", "hint", false, 10, 20);
        rule.setToday(LocalDate.of(2019, Month.AUGUST, 5));
        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", 2019, 1, 1)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2005, 1, 1)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2009, 8, 5)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 1999, 8, 5)));
        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", 1998, 8, 4)));
    }


}
