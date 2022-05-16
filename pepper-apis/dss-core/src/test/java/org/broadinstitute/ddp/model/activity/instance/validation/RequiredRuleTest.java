package org.broadinstitute.ddp.model.activity.instance.validation;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.broadinstitute.ddp.model.activity.types.DateFieldType.DAY;
import static org.broadinstitute.ddp.model.activity.types.DateFieldType.MONTH;
import static org.broadinstitute.ddp.model.activity.types.DateFieldType.YEAR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.MatrixAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedMatrixCell;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.AgreementQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.BoolQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.DateQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.MatrixQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.MatrixOption;
import org.broadinstitute.ddp.model.activity.instance.question.MatrixGroup;
import org.broadinstitute.ddp.model.activity.instance.question.MatrixRow;
import org.broadinstitute.ddp.model.activity.instance.question.TextQuestion;
import org.broadinstitute.ddp.model.activity.types.BooleanRenderMode;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistSelectMode;
import org.broadinstitute.ddp.model.activity.types.MatrixSelectMode;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.junit.Test;

public class RequiredRuleTest {

    @Test
    public void testRule() {
        RequiredRule<BoolAnswer> rule = new RequiredRule<>(1L, "hint", "msg", false);
        assertEquals(RuleType.REQUIRED, rule.getRuleType());
        assertEquals((Long) 1L, rule.getId());
        assertEquals("msg", rule.getDefaultMessage());
        assertEquals("hint", rule.getCorrectionHint());
    }

    @Test
    public void testRule_useCustomMessage() {
        RequiredRule<BoolAnswer> rule = new RequiredRule<>(1L, "custom message", "msg", false);
        assertEquals("custom message", rule.getMessage());
    }

    @Test
    public void testRule_fallsBackToDefaultMessage() {
        RequiredRule<BoolAnswer> rule = new RequiredRule<>(1L, null, "default message", false);
        assertEquals("default message", rule.getMessage());
    }

    @Test
    public void testValidate_boolAnswer() {
        BoolQuestion unused = new BoolQuestion("sid", 1L, emptyList(), emptyList(), 2L, 3L, BooleanRenderMode.RADIO_BUTTONS);
        RequiredRule<BoolAnswer> rule = new RequiredRule<>("msg", null, false);
        assertFalse(rule.validate(unused, null));
        assertFalse(rule.validate(unused, new BoolAnswer(1L, "q", "a", null)));
        assertTrue(rule.validate(unused, new BoolAnswer(1L, "q", "a", false)));
        assertTrue(rule.validate(unused, new BoolAnswer(1L, "q", "a", true)));
    }

    @Test
    public void testValidate_textAnswer() {
        TextQuestion unused = new TextQuestion("sid", 1L, 2L, emptyList(), emptyList(), TextInputType.TEXT);
        RequiredRule<TextAnswer> rule = new RequiredRule<>("msg", null, false);
        assertFalse(rule.validate(unused, null));
        assertFalse(rule.validate(unused, new TextAnswer(1L, "q", "a", null)));
        assertTrue(rule.validate(unused, new TextAnswer(1L, "q", "a", "text")));
        assertTrue(rule.validate(unused, new TextAnswer(1L, "q", "a", "1")));
        assertFalse(rule.validate(unused, new TextAnswer(1L, "q", "a", "")));
        assertFalse(rule.validate(unused, new TextAnswer(1L, "q", "a", " ")));
        assertFalse(rule.validate(unused, new TextAnswer(1L, "q", "a", "  ")));
    }

    @Test
    public void testValidate_picklistAnswer() {
        PicklistQuestion unused = new PicklistQuestion("sid", 1L, emptyList(), emptyList(), PicklistSelectMode.SINGLE,
                PicklistRenderMode.LIST, 2L, singletonList(
                        new PicklistOption("opt", 3L, null, null, false, false, false)));
        RequiredRule<PicklistAnswer> rule = new RequiredRule<>("msg", null, false);
        assertFalse(rule.validate(unused, null));
        assertFalse(rule.validate(unused, new PicklistAnswer(1L, "q", "a", emptyList())));
        assertTrue(rule.validate(unused, new PicklistAnswer(1L, "q", "a",
                singletonList(new SelectedPicklistOption("opt 1")))));
        assertTrue(rule.validate(unused, new PicklistAnswer(1L, "q", "a",
                Arrays.asList(new SelectedPicklistOption("opt 1"), new SelectedPicklistOption("opt 2")))));
    }

    @Test
    public void testValidate_matrixAnswer() {
        MatrixQuestion unused =
                new MatrixQuestion("sid", 1L, MatrixSelectMode.SINGLE, emptyList(), emptyList(),
                        List.of(new MatrixOption("opt 1", 3L, null, "DEFAULT", false),
                                new MatrixOption("opt 2", 3L, null, "group", false)),
                        singletonList(new MatrixRow("row 1", 4L, null)),
                        singletonList(new MatrixGroup("group", 5L)));
        RequiredRule<MatrixAnswer> rule = new RequiredRule<>("msg", null, false);
        assertFalse(rule.validate(unused, null));
        assertFalse(rule.validate(unused, new MatrixAnswer(1L, "sid", "a", emptyList())));
        assertFalse(rule.validate(unused, new MatrixAnswer(1L, "sid", "a",
                singletonList(new SelectedMatrixCell("row 1", "opt 1", "DEFAULT")))));
        assertTrue(rule.validate(unused, new MatrixAnswer(1L, "sid", "a",
                Arrays.asList(
                        new SelectedMatrixCell("row 1", "opt 1", "DEFAULT"),
                        new SelectedMatrixCell("row 1", "opt 2", "group")))));
    }

    @Test
    public void testValidate_agreementAnswer() {
        AgreementQuestion unused = new AgreementQuestion("sid", 1L, emptyList(), emptyList());
        RequiredRule<AgreementAnswer> rule = new RequiredRule<>("msg", null, false);
        assertFalse(rule.validate(unused, null));
        assertFalse(rule.validate(unused, new AgreementAnswer(1L, "q", "a", null)));
        assertTrue(rule.validate(unused, new AgreementAnswer(1L, "q", "a", false)));
        assertTrue(rule.validate(unused, new AgreementAnswer(1L, "q", "a", true)));
    }

    @Test
    public void testValidate_dateAnswer() {
        RequiredRule<DateAnswer> rule = new RequiredRule<>("msg", null, false);
        assertFalse(rule.validate(null, null));

        List<DateFieldType> fields = Arrays.asList(YEAR, MONTH, DAY);
        DateQuestion question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        runDate(false, rule, question, null, null, null);
        runDate(false, rule, question, 2018, null, null);
        runDate(false, rule, question, null, 3, null);
        runDate(false, rule, question, null, null, 12);
        runDate(false, rule, question, 2018, 3, null);
        runDate(false, rule, question, 2018, null, 12);
        runDate(false, rule, question, null, 3, 12);
        runDate(true, rule, question, 2018, 3, 12);

        fields = Arrays.asList(YEAR, MONTH);
        question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        runDate(false, rule, question, null, null, null);
        runDate(false, rule, question, 2018, null, null);
        runDate(false, rule, question, null, 3, null);
        runDate(false, rule, question, null, null, 12);
        runDate(true, rule, question, 2018, 3, null);
        runDate(false, rule, question, 2018, null, 12);
        runDate(false, rule, question, null, 3, 12);
        runDate(true, rule, question, 2018, 3, 12);

        fields = Arrays.asList(MONTH, DAY);
        question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        runDate(false, rule, question, null, null, null);
        runDate(false, rule, question, 2018, null, null);
        runDate(false, rule, question, null, 3, null);
        runDate(false, rule, question, null, null, 12);
        runDate(false, rule, question, 2018, 3, null);
        runDate(false, rule, question, 2018, null, 12);
        runDate(true, rule, question, null, 3, 12);
        runDate(true, rule, question, 2018, 3, 12);

        fields = Arrays.asList(YEAR, DAY);
        question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        runDate(false, rule, question, null, null, null);
        runDate(false, rule, question, 2018, null, null);
        runDate(false, rule, question, null, 3, null);
        runDate(false, rule, question, null, null, 12);
        runDate(false, rule, question, 2018, 3, null);
        runDate(true, rule, question, 2018, null, 12);
        runDate(false, rule, question, null, 3, 12);
        runDate(true, rule, question, 2018, 3, 12);

        fields = Arrays.asList(YEAR);
        question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        runDate(false, rule, question, null, null, null);
        runDate(true, rule, question, 2018, null, null);
        runDate(false, rule, question, null, 3, null);
        runDate(false, rule, question, null, null, 12);
        runDate(true, rule, question, 2018, 3, null);
        runDate(true, rule, question, 2018, null, 12);
        runDate(false, rule, question, null, 3, 12);
        runDate(true, rule, question, 2018, 3, 12);

        fields = Arrays.asList(MONTH);
        question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        runDate(false, rule, question, null, null, null);
        runDate(false, rule, question, 2018, null, null);
        runDate(true, rule, question, null, 3, null);
        runDate(false, rule, question, null, null, 12);
        runDate(true, rule, question, 2018, 3, null);
        runDate(false, rule, question, 2018, null, 12);
        runDate(true, rule, question, null, 3, 12);
        runDate(true, rule, question, 2018, 3, 12);

        fields = Arrays.asList(DAY);
        question = new DateQuestion("sid", 1L, emptyList(), emptyList(), DateRenderMode.TEXT, false, fields);
        runDate(false, rule, question, null, null, null);
        runDate(false, rule, question, 2018, null, null);
        runDate(false, rule, question, null, 3, null);
        runDate(true, rule, question, null, null, 12);
        runDate(false, rule, question, 2018, 3, null);
        runDate(true, rule, question, 2018, null, 12);
        runDate(true, rule, question, null, 3, 12);
        runDate(true, rule, question, 2018, 3, 12);
    }

    private void runDate(boolean expected, RequiredRule<DateAnswer> rule, DateQuestion question, Integer year, Integer month, Integer day) {
        assertEquals(expected, rule.validate(question, new DateAnswer(1L, "q", "a", year, month, day)));
    }
}
