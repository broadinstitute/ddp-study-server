package org.broadinstitute.ddp.model.activity.instance.question;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistSelectMode;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PicklistQuestionTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testEnsuresAtLeastOnePicklistOption() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("options");
        thrown.expectMessage("non-empty");
        new PicklistQuestion("sid", 1, emptyList(), emptyList(),
                PicklistSelectMode.SINGLE, PicklistRenderMode.LIST, null, emptyList());
    }

    @Test
    public void testEnsuresDropdownHasPicklistLabel() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("picklist label is required");
        thrown.expectMessage("dropdown");
        new PicklistQuestion("sid", 1, emptyList(), emptyList(),
                PicklistSelectMode.SINGLE, PicklistRenderMode.DROPDOWN, null, emptyList());
    }

    @Test
    public void testApplyRenderedTemplates_standardStyle() {
        Map<Long, String> rendered = new HashMap<>();
        rendered.put(1L, "<p>prompt</p>");
        rendered.put(2L, "this is <em>option</em> label");

        PicklistOption option = new PicklistOption("opt", 2, null, null, false, false);
        PicklistQuestion question = new PicklistQuestion("sid", 1, emptyList(), emptyList(),
                PicklistSelectMode.SINGLE, PicklistRenderMode.LIST, null, singletonList(option));
        question.applyRenderedTemplates(rendered::get, ContentStyle.STANDARD);

        assertTrue(HtmlConverter.hasSameValue(rendered.get(1L), question.getPrompt()));
        assertEquals("prompt", question.getTextPrompt());
        assertEquals(rendered.get(2L), question.getPicklistOptions().get(0).getOptionLabel());
    }

    @Test
    public void testApplyRenderedTemplates_basicStyle() {
        Map<Long, String> rendered = new HashMap<>();
        rendered.put(1L, "<p>prompt</p>");
        rendered.put(2L, "this is <em>option</em> label");

        PicklistOption option = new PicklistOption("opt", 2, null, null, false, false);
        PicklistQuestion question = new PicklistQuestion("sid", 1, emptyList(), emptyList(),
                PicklistSelectMode.SINGLE, PicklistRenderMode.LIST, null, singletonList(option));
        question.applyRenderedTemplates(rendered::get, ContentStyle.BASIC);

        assertEquals("prompt", question.getPrompt());
        assertEquals("this is option label", question.getPicklistOptions().get(0).getOptionLabel());
    }

    @Test
    public void testApplyRenderedTemplates_dropdownRenderModeForcesBasicStyleForPicklistSpecificContent() {
        Map<Long, String> rendered = new HashMap<>();
        rendered.put(1L, "<p>prompt</p>");
        rendered.put(2L, "this is <em>picklist</em> label");
        rendered.put(3L, "this is <em>option</em> label");

        PicklistOption option = new PicklistOption("opt", 3, null, null, false, false);
        PicklistQuestion question = new PicklistQuestion("sid", 1, emptyList(), emptyList(),
                PicklistSelectMode.SINGLE, PicklistRenderMode.DROPDOWN, 2L, singletonList(option));
        question.applyRenderedTemplates(rendered::get, ContentStyle.STANDARD);

        assertEquals(rendered.get(1L), question.getPrompt());
        assertEquals("this is picklist label", question.getPicklistLabel());
        assertEquals("this is option label", question.getPicklistOptions().get(0).getOptionLabel());
    }
}
