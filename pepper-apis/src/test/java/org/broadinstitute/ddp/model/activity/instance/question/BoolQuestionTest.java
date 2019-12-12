package org.broadinstitute.ddp.model.activity.instance.question;

import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.junit.Test;

public class BoolQuestionTest {

    @Test
    public void testApplyRenderedTemplates_standardStyle() {
        Map<Long, String> rendered = new HashMap<>();
        rendered.put(1L, "<p>prompt</p>");
        rendered.put(2L, "prompt");
        rendered.put(3L, "<em>very much</em> yes");
        rendered.put(4L, "<strong>definitely</strong> no");

        BoolQuestion question = new BoolQuestion("sid", 1, emptyList(), emptyList(), 3, 4);
        question.applyRenderedTemplates(rendered::get, ContentStyle.STANDARD);

        assertTrue(HtmlConverter.hasSameValue(rendered.get(1L), question.getPrompt()));
        assertEquals(rendered.get(2L), question.getTextPrompt());
        assertEquals(rendered.get(3L), question.getTrueContent());
        assertEquals(rendered.get(4L), question.getFalseContent());
    }

    @Test
    public void testApplyRenderedTemplates_basicStyle() {
        Map<Long, String> rendered = new HashMap<>();
        rendered.put(1L, "<p><b>prompt</b></p>");
        rendered.put(2L, "<em>very much</em> yes");
        rendered.put(3L, "<strong>definitely</strong> no");

        BoolQuestion question = new BoolQuestion("sid", 1, emptyList(), emptyList(), 2, 3);
        question.applyRenderedTemplates(rendered::get, ContentStyle.BASIC);

        assertTrue(HtmlConverter.hasSameValue("<b>prompt</b>", question.getPrompt()));
        assertEquals("prompt", question.getTextPrompt());
        assertEquals("very much yes", question.getTrueContent());
        assertEquals("definitely no", question.getFalseContent());
    }
}
