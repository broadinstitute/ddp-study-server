package org.broadinstitute.ddp.model.activity.instance.question;

import org.broadinstitute.ddp.content.ContentStyle;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class MatrixGroupTest {

    @Test
    public void testApplyRenderedTemplates_standardStyle() {
        Map<Long, String> rendered = new HashMap<>();
        rendered.put(1L, "this is <em>group</em> label");

        MatrixGroup group = new MatrixGroup("sid", 1L);
        group.applyRenderedTemplates(rendered::get, ContentStyle.STANDARD);

        assertEquals(rendered.get(1L), group.getName());
    }

    @Test
    public void testApplyRenderedTemplates_basicStyle() {
        Map<Long, String> rendered = new HashMap<>();
        rendered.put(1L, "this is <em>group</em> label");

        MatrixGroup group = new MatrixGroup("sid", 1L);
        group.applyRenderedTemplates(rendered::get, ContentStyle.BASIC);

        assertEquals("this is group label", group.getName());
    }
}
