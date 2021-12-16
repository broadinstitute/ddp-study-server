package org.broadinstitute.ddp.model.activity.instance.question;

import org.broadinstitute.ddp.content.ContentStyle;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class MatrixRowTest {

    @Test
    public void testApplyRenderedTemplates_standardStyle() {
        Map<Long, String> rendered = new HashMap<>();
        rendered.put(1L, "this is <em>row</em> label");

        MatrixRow row = new MatrixRow("sid", 1L, null);
        row.applyRenderedTemplates(rendered::get, ContentStyle.STANDARD);

        assertEquals(rendered.get(1L), row.getQuestionLabel());
    }

    @Test
    public void testApplyRenderedTemplates_basicStyle() {
        Map<Long, String> rendered = new HashMap<>();
        rendered.put(1L, "this is <em>row</em> label");

        MatrixRow row = new MatrixRow("sid", 1L, null);
        row.applyRenderedTemplates(rendered::get, ContentStyle.BASIC);

        assertEquals("this is row label", row.getQuestionLabel());
    }
}
