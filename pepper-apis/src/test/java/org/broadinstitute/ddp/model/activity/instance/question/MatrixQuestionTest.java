package org.broadinstitute.ddp.model.activity.instance.question;

import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.model.activity.types.MatrixSelectMode;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MatrixQuestionTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testEnsuresAtLeastOneMatrixOption() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("options");
        thrown.expectMessage("non-empty");
        new MatrixQuestion("sid", 1, MatrixSelectMode.SINGLE, emptyList(), emptyList(),
                emptyList(), emptyList(), emptyList());
    }

    @Test
    public void testEnsuresAtLeastOneMatrixRow() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("rows");
        thrown.expectMessage("non-empty");
        new MatrixQuestion("sid", 1, MatrixSelectMode.SINGLE, emptyList(), emptyList(),
                List.of(new MatrixOption("opt", 2, "DEFAULT")), emptyList(), emptyList());
    }

    @Test
    public void testApplyRenderedTemplates_standardStyle() {
        Map<Long, String> rendered = new HashMap<>();
        rendered.put(1L, "<p>prompt</p>");
        rendered.put(2L, "this is <em>option</em> label");
        rendered.put(3L, "this is <em>row</em> label");

        MatrixOption option = new MatrixOption("opt", 2, "DEFAULT");
        MatrixRow row = new MatrixRow("row", 3, null);
        MatrixQuestion question = new MatrixQuestion("sid", 1, MatrixSelectMode.SINGLE, emptyList(), emptyList(),
                singletonList(option), singletonList(row), null);
        question.applyRenderedTemplates(rendered::get, ContentStyle.STANDARD);

        assertTrue(HtmlConverter.hasSameValue(rendered.get(1L), question.getPrompt()));
        assertEquals("prompt", question.getTextPrompt());
        assertEquals(rendered.get(2L), question.getMatrixOptions().get(0).getOptionLabel());
        assertEquals(rendered.get(3L), question.getMatrixQuestionRows().get(0).getQuestionLabel());
    }

    @Test
    public void testApplyRenderedTemplates_basicStyle() {
        Map<Long, String> rendered = new HashMap<>();
        rendered.put(1L, "<p>prompt</p>");
        rendered.put(2L, "this is <em>option</em> label");
        rendered.put(3L, "this is <em>row</em> label");

        MatrixOption option = new MatrixOption("opt", 2, "DEFAULT");
        MatrixRow row = new MatrixRow("row", 3, null);
        MatrixQuestion question = new MatrixQuestion("sid", 1, MatrixSelectMode.SINGLE, emptyList(), emptyList(),
                singletonList(option), singletonList(row), null);
        question.applyRenderedTemplates(rendered::get, ContentStyle.BASIC);

        assertEquals("prompt", question.getPrompt());
        assertEquals("this is option label", question.getMatrixOptions().get(0).getOptionLabel());
        assertEquals("this is row label", question.getMatrixQuestionRows().get(0).getQuestionLabel());
    }
}
