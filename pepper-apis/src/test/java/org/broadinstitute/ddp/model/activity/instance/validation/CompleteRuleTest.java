package org.broadinstitute.ddp.model.activity.instance.validation;

import static java.util.Collections.emptyList;
import static org.broadinstitute.ddp.model.activity.types.DateFieldType.DAY;
import static org.broadinstitute.ddp.model.activity.types.DateFieldType.MONTH;
import static org.broadinstitute.ddp.model.activity.types.DateFieldType.YEAR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.BoolQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.DateQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.TextQuestion;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.junit.Test;

public class CompleteRuleTest {

    @Test
    public void testRule() {
        CompleteRule<BoolAnswer> rule = new CompleteRule<>(1L, "msg", "hint", false);
        assertEquals(RuleType.COMPLETE, rule.getRuleType());
        assertEquals((Long) 1L, rule.getId());
        assertEquals("msg", rule.getDefaultMessage());
        assertEquals("hint", rule.getCorrectionHint());
    }

    @Test
    public void testValidate_dateAnswer() {
        CompleteRule<DateAnswer> rule = new CompleteRule<>("msg", null, false);
        assertFalse(rule.validate(null, null));

        List<DateFieldType> fields = Arrays.asList(YEAR, MONTH, DAY);
        DateQuestion question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);

        assertFalse(rule.validate(question, null));
        assertFalse(rule.validate(question, new DateAnswer(1L, "q", "a", 2018, null, null)));
        assertFalse(rule.validate(question, new DateAnswer(1L, "q", "a", null, 3, null)));
        assertFalse(rule.validate(question, new DateAnswer(1L, "q", "a", null, null, 14)));
        assertFalse(rule.validate(question, new DateAnswer(1L, "q", "a", 2018, 3, null)));
        assertFalse(rule.validate(question, new DateAnswer(1L, "q", "a", 2018, null, 14)));
        assertFalse(rule.validate(question, new DateAnswer(1L, "q", "a", null, 3, 14)));
        assertTrue(rule.validate(question, new DateAnswer(1L, "q", "a", 2018, 3, 14)));
        assertTrue(rule.validate(question, new DateAnswer(1L, "q", "a", null, null, null)));
    }

    @Test
    public void testValidate_unhanledAnswerTypes_pass() {
        CompleteRule<BoolAnswer> ruleBool = new CompleteRule<>("msg", null, false);
        BoolQuestion boolQ = new BoolQuestion("sid", 1L, emptyList(), emptyList(), 2L, 3L);
        assertFalse(ruleBool.validate(boolQ, null));
        assertTrue(ruleBool.validate(boolQ, new BoolAnswer(1L, "q", "a", null)));
        assertTrue(ruleBool.validate(boolQ, new BoolAnswer(1L, "q", "a", false)));
        assertTrue(ruleBool.validate(boolQ, new BoolAnswer(1L, "q", "a", true)));

        CompleteRule<TextAnswer> ruleText = new CompleteRule<>("msg", null, false);
        TextQuestion textQ = new TextQuestion("sid", 1L, 2L, emptyList(), emptyList(), TextInputType.TEXT);
        assertFalse(ruleText.validate(textQ, null));
        assertTrue(ruleText.validate(textQ, new TextAnswer(1L, "q", "a", null)));
        assertTrue(ruleText.validate(textQ, new TextAnswer(1L, "q", "a", "text")));
    }
}
