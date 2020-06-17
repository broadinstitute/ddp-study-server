package org.broadinstitute.ddp.model.activity.instance.question;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.ddp.content.ContentStyle;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PicklistOptionTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testEnsureDetailTemplateIsProvided() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("detail label must be provided");
        new PicklistOption("sid", 1L, null, null, true, false);
    }

    @Test
    public void testApplyRenderedTemplates_standardStyle() {
        Map<Long, String> rendered = new HashMap<>();
        rendered.put(1L, "this is <em>option</em> label");
        rendered.put(2L, "this is <em>detail</em> label");

        PicklistOption option = new PicklistOption("sid", 1L, null, 2L, true, false);
        option.applyRenderedTemplates(rendered::get, ContentStyle.STANDARD);

        assertEquals(rendered.get(1L), option.getOptionLabel());
        assertEquals(rendered.get(2L), option.getDetailLabel());
    }

    @Test
    public void testApplyRenderedTemplates_basicStyle() {
        Map<Long, String> rendered = new HashMap<>();
        rendered.put(1L, "this is <em>option</em> label");
        rendered.put(2L, "this is <em>detail</em> label");

        PicklistOption option = new PicklistOption("sid", 1L, null, 2L, true, false);
        option.applyRenderedTemplates(rendered::get, ContentStyle.BASIC);

        assertEquals("this is option label", option.getOptionLabel());
        assertEquals("this is detail label", option.getDetailLabel());
    }
}
