package org.broadinstitute.ddp.model.activity.instance.validation;

import static java.util.Collections.emptyList;
import static org.broadinstitute.ddp.model.activity.types.DateFieldType.DAY;
import static org.broadinstitute.ddp.model.activity.types.DateFieldType.MONTH;
import static org.broadinstitute.ddp.model.activity.types.DateFieldType.YEAR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.DateQuestion;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class DateRangeRuleTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testRule() {
        DateRangeRule rule = DateRangeRule.of(1L, "msg", "hint", false, LocalDate.of(2018, 3, 14), LocalDate.of(2018, 10, 15));
        assertEquals(RuleType.DATE_RANGE, rule.getRuleType());
        assertEquals((Long) 1L, rule.getId());
        assertEquals("msg", rule.getDefaultMessage());
        assertEquals("hint", rule.getCorrectionHint());
        assertEquals("2018-03-14", rule.getStartDate().toString());
        assertEquals("2018-10-15", rule.getEndDate().toString());
    }

    @Test
    public void testValidate_noValue() {
        List<DateFieldType> fields = Arrays.asList(YEAR, MONTH, DAY);
        DateQuestion question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        DateRangeRule rule = DateRangeRule.of("msg", "hint", false, LocalDate.now(), LocalDate.now());
        assertFalse(rule.validate(question, null));
    }

    @Test
    public void testValidate_emptyValue() {
        List<DateFieldType> fields = Arrays.asList(YEAR, MONTH, DAY);
        DateQuestion question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        DateRangeRule rule = DateRangeRule.of("msg", "hint", false, LocalDate.now(), LocalDate.now());
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", null, null, null)));
    }

    @Test
    public void testValidate_noStart() {
        List<DateFieldType> fields = Arrays.asList(YEAR, MONTH, DAY);
        DateQuestion question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        DateRangeRule rule = DateRangeRule.of("msg", "hint", false, null, LocalDate.of(2018, 3, 14));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 1988, 1, 1)));
    }

    @Test
    public void testValidate_noEnd() {
        List<DateFieldType> fields = Arrays.asList(YEAR, MONTH, DAY);
        DateQuestion question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        DateRangeRule rule = DateRangeRule.of("msg", "hint", false, LocalDate.of(2018, 3, 14), null);
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2222, 1, 1)));
    }

    @Test
    public void testValidate_hasStart() {
        List<DateFieldType> fields = Arrays.asList(YEAR, MONTH, DAY);
        DateQuestion question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        DateRangeRule rule = DateRangeRule.of("msg", "hint", false, LocalDate.of(2018, 3, 14), null);
        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", 1988, 1, 13)));
        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", 2018, 1, 13)));
        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", 2018, 3, 13)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2018, 3, 14)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2018, 3, 15)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2222, 3, 15)));
    }

    @Test
    public void testValidate_hasEnd() {
        List<DateFieldType> fields = Arrays.asList(YEAR, MONTH, DAY);
        DateQuestion question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        DateRangeRule rule = DateRangeRule.of("msg", "hint", false, null, LocalDate.of(2018, 3, 14));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 1988, 3, 13)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2018, 3, 13)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2018, 1, 13)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2018, 3, 14)));
        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", 2018, 3, 15)));
        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", 2222, 3, 15)));
    }

    @Test
    public void testValidate_withinRange() {
        List<DateFieldType> fields = Arrays.asList(YEAR, MONTH, DAY);
        DateQuestion question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        DateRangeRule rule = DateRangeRule.of("msg", "hint", false, LocalDate.of(1900, 1, 1), LocalDate.of(2018, 3, 14));
        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", 1899, 1, 1)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 1900, 1, 1)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 1900, 1, 2)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 1988, 3, 14)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2018, 3, 13)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2018, 3, 14)));
        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", 2018, 3, 15)));
    }

    @Test
    public void testValidate_fullDate() {
        List<DateFieldType> fields = Arrays.asList(YEAR, MONTH, DAY);
        DateQuestion question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        DateRangeRule rule = DateRangeRule.of("msg", "hint", false, LocalDate.of(1900, 1, 1), LocalDate.of(2018, 3, 14));
        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", 1899, 1, 1)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2002, 1, 1)));
        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", 2222, 1, 1)));
    }

    @Test
    public void testValidate_yearMonth() {
        List<DateFieldType> fields = Arrays.asList(YEAR, MONTH);
        DateQuestion question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        DateRangeRule rule = DateRangeRule.of("msg", "hint", false, LocalDate.of(1900, 1, 1), LocalDate.of(2018, 3, 14));
        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", 1899, 1, null)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 1900, 1, null)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 1900, 2, null)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2018, 3, null)));
        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", 2018, 4, null)));
    }

    @Test
    public void testValidate_monthDay() {
        List<DateFieldType> fields = Arrays.asList(MONTH, DAY);
        DateQuestion question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        DateRangeRule rule = DateRangeRule.of("msg", "hint", false, LocalDate.of(1900, 2, 2), LocalDate.of(1900, 3, 3));
        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", null, 1, 1)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", null, 2, 2)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", null, 2, 3)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", null, 3, 2)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", null, 3, 3)));
        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", null, 3, 4)));
    }

    @Test
    public void testValidate_yearDay() {
        List<DateFieldType> fields = Arrays.asList(YEAR, DAY);
        DateQuestion question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        DateRangeRule rule = DateRangeRule.of("msg", "hint", false, LocalDate.of(1900, 2, 2), LocalDate.of(2018, 3, 3));
        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", 1899, null, 2)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 1900, null, 2)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 1900, null, 3)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 1988, null, 29)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2018, null, 2)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2018, null, 3)));
    }

    @Test
    public void testValidate_yearOnly() {
        List<DateFieldType> fields = Arrays.asList(YEAR);
        DateQuestion question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        DateRangeRule rule = DateRangeRule.of("msg", "hint", false, LocalDate.of(1900, 2, 2), LocalDate.of(2018, 2, 2));
        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", 1899, null, null)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 1900, null, null)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 1988, null, null)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", 2018, null, null)));
        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", 2019, null, null)));
    }

    @Test
    public void testValidate_monthOnly() {
        List<DateFieldType> fields = Arrays.asList(MONTH);
        DateQuestion question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        DateRangeRule rule = DateRangeRule.of("msg", "hint", false, LocalDate.of(1900, 2, 2), LocalDate.of(1900, 9, 2));
        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", null, 1, null)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", null, 2, null)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", null, 5, null)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", null, 9, null)));
        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", null, 10, null)));
    }

    @Test
    public void testValidate_dayOnly() {
        List<DateFieldType> fields = Arrays.asList(DAY);
        DateQuestion question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        DateRangeRule rule = DateRangeRule.of("msg", "hint", false, LocalDate.of(1900, 2, 2), LocalDate.of(1900, 2, 22));
        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", null, null, 1)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", null, null, 2)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", null, null, 10)));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", null, null, 22)));
        assertFalse(rule.validate(question, new DateAnswer(null, "q", "a", null, null, 23)));
    }

    @Test
    public void testValidate_specifiedFieldsNotPresent() {
        List<DateFieldType> fields = Arrays.asList(YEAR);
        DateQuestion question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        DateRangeRule rule = DateRangeRule.of("msg", "hint", false, LocalDate.of(1900, 2, 2), LocalDate.of(1900, 2, 22));
        assertTrue(rule.validate(question, new DateAnswer(null, "q", "a", null, 3, 12)));
    }
}
