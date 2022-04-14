package org.broadinstitute.ddp.model.activity.instance;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.ddp.content.ContentStyle;
import org.junit.Test;

public class ContentBlockTest {

    @Test
    public void testApplyRenderedTemplates_standardStyle() {
        Map<Long, String> rendered = new HashMap<>();
        rendered.put(1L, "<p>title</p>");
        rendered.put(2L, "<p>body</p>");

        ContentBlock content = new ContentBlock(1L, 2L);
        content.applyRenderedTemplates(rendered::get, ContentStyle.STANDARD);

        assertEquals(rendered.get(1L), content.getTitle());
        assertEquals(rendered.get(2L), content.getBody());
    }

    @Test
    public void testApplyRenderedTemplates_basicStyleOnlyConvertsTitleToPlainText() {
        Map<Long, String> rendered = new HashMap<>();
        rendered.put(1L, "<p>title</p>");
        rendered.put(2L, "<p>body</p>");

        ContentBlock content = new ContentBlock(1L, 2L);
        content.applyRenderedTemplates(rendered::get, ContentStyle.BASIC);

        assertEquals("title", content.getTitle());
        assertEquals(rendered.get(2L), content.getBody());
    }
}
