package org.broadinstitute.ddp.model.activity.instance.question;

import org.broadinstitute.ddp.content.ContentStyle;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class MatrixOptionTest {

    @Test
    public void testApplyRenderedTemplates_standardStyle() {
        Map<Long, String> rendered = new HashMap<>();
        rendered.put(1L, "this is <em>option</em> label");

        MatrixOption option = new MatrixOption("sid", 1L, null, true);
        option.applyRenderedTemplates(rendered::get, ContentStyle.STANDARD);

        assertEquals(rendered.get(1L), option.getOptionLabel());
    }

    @Test
    public void testApplyRenderedTemplates_basicStyle() {
        Map<Long, String> rendered = new HashMap<>();
        rendered.put(1L, "this is <em>option</em> label");

        MatrixOption option = new MatrixOption("sid", 1L, null, true);
        option.applyRenderedTemplates(rendered::get, ContentStyle.BASIC);

        assertEquals("this is option label", option.getOptionLabel());
    }
}
