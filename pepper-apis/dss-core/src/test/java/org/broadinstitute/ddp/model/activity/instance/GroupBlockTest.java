package org.broadinstitute.ddp.model.activity.instance;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.junit.Test;

public class GroupBlockTest {

    @Test
    public void testApplyRenderedTemplates_standardStyle() {
        Map<Long, String> rendered = new HashMap<>();
        rendered.put(1L, "<p>title</p>");
        rendered.put(2L, "<p>nested content</p>");

        ContentBlock nested = new ContentBlock(2L);
        GroupBlock group = new GroupBlock(ListStyleHint.NONE, 1L);
        group.getNested().add(nested);

        group.applyRenderedTemplates(rendered::get, ContentStyle.STANDARD);

        assertEquals(rendered.get(1L), group.getTitle());
        assertEquals(rendered.get(2L), nested.getBody());
    }

    @Test
    public void testApplyRenderedTemplates_basicStyle() {
        Map<Long, String> rendered = new HashMap<>();
        rendered.put(1L, "<p>title</p>");
        rendered.put(2L, "<p>nested content title</p>");
        rendered.put(3L, "<p>nested content body</p>");

        ContentBlock nested = new ContentBlock(2L, 3L);
        GroupBlock group = new GroupBlock(ListStyleHint.NONE, 1L);
        group.getNested().add(nested);

        group.applyRenderedTemplates(rendered::get, ContentStyle.BASIC);

        assertEquals("title", group.getTitle());
        assertEquals("nested content title", nested.getTitle());
        assertEquals(rendered.get(3L), nested.getBody());
    }
}
