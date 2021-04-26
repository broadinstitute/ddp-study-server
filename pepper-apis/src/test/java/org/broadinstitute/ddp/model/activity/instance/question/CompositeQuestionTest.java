package org.broadinstitute.ddp.model.activity.instance.question;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.model.activity.instance.answer.AnswerRow;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.instance.validation.LengthRule;
import org.broadinstitute.ddp.model.activity.instance.validation.RequiredRule;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.junit.Test;

public class CompositeQuestionTest {

    @Test
    public void testApplyRenderedTemplates_standardStyle() {
        Map<Long, String> rendered = new HashMap<>();
        rendered.put(1L, "<p>parent prompt</p>");
        rendered.put(2L, "this is <em>add button</em> label");
        rendered.put(3L, "this is <em>additional item</em> label");
        rendered.put(4L, "<p>child prompt</p>");

        TextQuestion child = new TextQuestion("child", 4, null, emptyList(), emptyList(), TextInputType.TEXT);
        CompositeQuestion parent = new CompositeQuestion("parent", 1L, emptyList(), false, 2L, 3L, singletonList(child), emptyList());
        parent.applyRenderedTemplates(rendered::get, ContentStyle.STANDARD);

        assertTrue(HtmlConverter.hasSameValue(rendered.get(1L), parent.getPrompt()));
        assertEquals("parent prompt", parent.getTextPrompt());
        assertEquals(rendered.get(2L), parent.getAddButtonText());
        assertEquals(rendered.get(3L), parent.getAdditionalItemText());
        assertTrue(HtmlConverter.hasSameValue(rendered.get(4L), child.getPrompt()));
    }

    @Test
    public void testApplyRenderedTemplates_basicStyle() {
        Map<Long, String> rendered = new HashMap<>();
        rendered.put(1L, "<b>parent<b> prompt");
        rendered.put(2L, "this is <em>add button</em> label");
        rendered.put(3L, "this is <em>additional item</em> label");
        rendered.put(4L, "<p>child prompt</p>");

        TextQuestion child = new TextQuestion("child", 4, null, emptyList(), emptyList(), TextInputType.TEXT);
        CompositeQuestion parent = new CompositeQuestion("parent", 1L, emptyList(), false, 2L, 3L, singletonList(child), emptyList());
        parent.applyRenderedTemplates(rendered::get, ContentStyle.BASIC);

        assertTrue(HtmlConverter.hasSameValue(rendered.get(1L), parent.getPrompt()));
        assertEquals("parent prompt", parent.getTextPrompt());
        assertEquals("this is add button label", parent.getAddButtonText());
        assertEquals("this is additional item label", parent.getAdditionalItemText());
        assertEquals("child prompt", child.getPrompt());
    }

    @Test
    public void testIsComplete_childQuestionNotRequired() {
        TextQuestion child = new TextQuestion("child", 4, null, emptyList(), new ArrayList<>(), TextInputType.TEXT);
        CompositeQuestion parent = new CompositeQuestion("parent", 1L, emptyList(), false, 2L, 3L, singletonList(child), emptyList());
        assertTrue(parent.passesDeferredValidations());
    }

    @Test
    public void testIsComplete_childQuestionRequired_noAnswers() {
        TextQuestion child = new TextQuestion("child", 4, null, emptyList(), new ArrayList<>(), TextInputType.TEXT);
        child.getValidations().add(new RequiredRule<>("child required", null, false));
        CompositeQuestion parent = new CompositeQuestion("parent", 1L, emptyList(), false, 2L, 3L, singletonList(child), emptyList());
        assertFalse(parent.passesDeferredValidations());
    }

    @Test
    public void testIsComplete_childQuestionRequired_noAnswerForChild() {
        TextQuestion child1 = new TextQuestion("child1", 4, null, emptyList(), new ArrayList<>(), TextInputType.TEXT);
        child1.getValidations().add(new RequiredRule<>("child1 required", null, false));
        TextQuestion child2 = new TextQuestion("child2", 4, null, emptyList(), new ArrayList<>(), TextInputType.TEXT);
        child2.getValidations().add(new RequiredRule<>("child2 required", null, false));
        CompositeQuestion parent = new CompositeQuestion("parent", 1L, emptyList(), false, 2L, 3L,
                Arrays.asList(child1, child2), new ArrayList<>());

        CompositeAnswer answer = new CompositeAnswer(1L, "parent", "a");
        answer.setValue(singletonList(new AnswerRow(singletonList(
                new TextAnswer(2L, "child2", "b", "abc")
        ))));
        parent.setAnswers(singletonList(answer));

        assertFalse(parent.passesDeferredValidations());
    }

    @Test
    public void testIsComplete_childQuestionRequired_notAllRowsAnsweredForChild() {
        TextQuestion child1 = new TextQuestion("child1", 4, null, emptyList(), new ArrayList<>(), TextInputType.TEXT);
        child1.getValidations().add(new RequiredRule<>("child1 required", null, false));
        TextQuestion child2 = new TextQuestion("child2", 4, null, emptyList(), new ArrayList<>(), TextInputType.TEXT);
        child2.getValidations().add(new RequiredRule<>("child2 required", null, false));
        CompositeQuestion parent = new CompositeQuestion("parent", 1L, emptyList(), false, 2L, 3L,
                Arrays.asList(child1, child2), new ArrayList<>());

        CompositeAnswer answer = new CompositeAnswer(1L, "parent", "a");
        answer.setValue(Arrays.asList(
                new AnswerRow(singletonList(new TextAnswer(2L, "child2", "b", "child2 row 1"))),
                new AnswerRow(Arrays.asList(
                        new TextAnswer(3L, "child1", "c", "child1 row 2"),
                        new TextAnswer(4L, "child2", "d", "child2 row 2")
                ))));
        parent.setAnswers(singletonList(answer));

        assertFalse(parent.passesDeferredValidations());
    }

    @Test
    public void testIsComplete_childQuestionRequired_allRowsAnswered() {
        TextQuestion child1 = new TextQuestion("child1", 4, null, emptyList(), new ArrayList<>(), TextInputType.TEXT);
        child1.getValidations().add(new RequiredRule<>("child1 required", null, false));
        TextQuestion child2 = new TextQuestion("child2", 4, null, emptyList(), new ArrayList<>(), TextInputType.TEXT);
        child2.getValidations().add(new RequiredRule<>("child2 required", null, false));
        CompositeQuestion parent = new CompositeQuestion("parent", 1L, emptyList(), false, 2L, 3L,
                Arrays.asList(child1, child2), new ArrayList<>());

        CompositeAnswer answer = new CompositeAnswer(1L, "parent", "a");
        answer.setValue(Arrays.asList(
                new AnswerRow(Arrays.asList(
                        new TextAnswer(2L, "child1", "b", "child1 row 1"),
                        new TextAnswer(3L, "child2", "c", "child2 row 1"))),
                new AnswerRow(Arrays.asList(
                        new TextAnswer(4L, "child1", "d", "child1 row 2"),
                        new TextAnswer(5L, "child2", "e", "child2 row 2")
                ))));
        parent.setAnswers(singletonList(answer));

        assertTrue(parent.passesDeferredValidations());
    }

    @Test
    public void testIsComplete_onlyRequiredChildIsChecked() {
        TextQuestion child1 = new TextQuestion("child1", 4, null, emptyList(), new ArrayList<>(), TextInputType.TEXT);
        child1.getValidations().add(new RequiredRule<>("child1 required", null, false));
        TextQuestion child2 = new TextQuestion("child2", 4, null, emptyList(), new ArrayList<>(), TextInputType.TEXT);
        CompositeQuestion parent = new CompositeQuestion("parent", 1L, emptyList(), false, 2L, 3L,
                Arrays.asList(child1, child2), new ArrayList<>());

        CompositeAnswer answer = new CompositeAnswer(1L, "parent", "a");
        answer.setValue(singletonList(
                new AnswerRow(singletonList(
                        new TextAnswer(2L, "child1", "b", "child1 row 1")))));
        // no value for child 2
        parent.setAnswers(singletonList(answer));

        assertTrue(parent.passesDeferredValidations());
    }

    @Test
    public void testIsComplete_allowSaveIsChecked() {
        TextQuestion child1 = new TextQuestion("child1", 4, null, emptyList(), new ArrayList<>(), TextInputType.TEXT);
        child1.getValidations().add(LengthRule.of("length", null, true, 100, null));
        CompositeQuestion parent = new CompositeQuestion("parent", 1L, emptyList(), false, 2L, 3L,
                singletonList(child1), new ArrayList<>());

        CompositeAnswer answer = new CompositeAnswer(1L, "parent", "a");
        answer.setValue(singletonList(
                new AnswerRow(singletonList(
                        new TextAnswer(2L, "child1", "b", "too short")))));
        parent.setAnswers(singletonList(answer));

        assertFalse(parent.passesDeferredValidations());
    }
}
