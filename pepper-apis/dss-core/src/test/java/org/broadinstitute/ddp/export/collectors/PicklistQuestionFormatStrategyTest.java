package org.broadinstitute.ddp.export.collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.junit.Before;
import org.junit.Test;

public class PicklistQuestionFormatStrategyTest {

    private PicklistQuestionFormatStrategy fmt;

    @Before
    public void setup() {
        fmt = new PicklistQuestionFormatStrategy();
    }

    @Test
    public void testMappings() {
        PicklistQuestionDef def = PicklistQuestionDef.buildMultiSelect(PicklistRenderMode.LIST, "sid", Template.text(""))
                .addOption(new PicklistOptionDef("op1", Template.text("")))
                .addOption(new PicklistOptionDef("op2", Template.text("label"), Template.text("details")))
                .build();
        Map<String, Object> actual = fmt.mappings(def);

        assertNotNull(actual);
        assertEquals(2, actual.size());
        assertTrue(actual.containsKey("sid"));
        assertEquals("text", ((Map) actual.get("sid")).get("type"));
        assertTrue(actual.containsKey("sid_op2_DETAILS"));
        assertEquals("text", ((Map) actual.get("sid_op2_DETAILS")).get("type"));
    }

    @Test
    public void testHeaders_singleColumnForSelectedOptions() {
        PicklistQuestionDef def = PicklistQuestionDef.buildMultiSelect(PicklistRenderMode.LIST, "sid", Template.text(""))
                .addOption(new PicklistOptionDef("op1", Template.text("")))
                .build();
        List<String> actual = fmt.headers(def);

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("sid", actual.get(0));
    }

    @Test
    public void testHeaders_additionalColumnsPerDetails() {
        PicklistQuestionDef def = PicklistQuestionDef.buildMultiSelect(PicklistRenderMode.LIST, "sid", Template.text(""))
                .addOption(new PicklistOptionDef("op1", Template.text("has"), Template.text("details")))
                .addOption(new PicklistOptionDef("op2", Template.text("no details")))
                .addOption(new PicklistOptionDef("op3", Template.text("has"), Template.text("details")))
                .build();
        List<String> actual = fmt.headers(def);

        assertNotNull(actual);
        assertEquals(3, actual.size());
        assertEquals("sid", actual.get(0));
        assertEquals("sid_op1_DETAILS", actual.get(1));
        assertEquals("sid_op3_DETAILS", actual.get(2));
    }

    @Test
    public void testCollect_answerWithNoSelections() {
        Map<String, String> actual = fmt.collect(buildQuestion(), buildAnswer());

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("", actual.get("sid"));
    }

    @Test
    public void testCollect_selectedOptionsSortedInDefinitionOrder() {
        Map<String, String> actual = fmt.collect(buildQuestion(), buildAnswer(
                new SelectedPicklistOption("op2"),
                new SelectedPicklistOption("op1")));

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertTrue(actual.containsKey("sid"));
        assertEquals("op1,op2", actual.get("sid"));
    }

    @Test
    public void testCollect_withDetails() {
        Map<String, String> actual = fmt.collect(buildQuestion(), buildAnswer(
                new SelectedPicklistOption("op2"),
                new SelectedPicklistOption("op3", "op3 details"),
                new SelectedPicklistOption("op1", "This is op1 details, with lots of stuff.")));

        assertNotNull(actual);
        assertEquals(3, actual.size());
        assertEquals("op1,op2,op3", actual.get("sid"));
        assertEquals("op3 details", actual.get("sid_op3_DETAILS"));
        assertEquals("This is op1 details, with lots of stuff.", actual.get("sid_op1_DETAILS"));
    }

    private PicklistQuestionDef buildQuestion() {
        return PicklistQuestionDef.buildMultiSelect(PicklistRenderMode.LIST, "sid", Template.text(""))
                .addOption(new PicklistOptionDef("op1", Template.text("has"), Template.text("details")))
                .addOption(new PicklistOptionDef("op2", Template.text("no details")))
                .addOption(new PicklistOptionDef("op3", Template.text("has"), Template.text("details")))
                .build();
    }

    private PicklistAnswer buildAnswer(SelectedPicklistOption... selected) {
        return new PicklistAnswer(1L, "sid", "abc", Arrays.asList(selected));
    }
}
