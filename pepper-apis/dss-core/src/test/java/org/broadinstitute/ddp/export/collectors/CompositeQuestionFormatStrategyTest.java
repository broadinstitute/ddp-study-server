package org.broadinstitute.ddp.export.collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.answer.AnswerRow;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.junit.Before;
import org.junit.Test;

public class CompositeQuestionFormatStrategyTest {

    private CompositeQuestionFormatStrategy fmt;

    @Before
    public void setup() {
        fmt = new CompositeQuestionFormatStrategy();
    }

    @Test
    public void testMappings() {
        CompositeQuestionDef def = CompositeQuestionDef.builder()
                .setStableId("sid")
                .setPrompt(Template.text("composite prompt"))
                .addChildrenQuestions(
                        TextQuestionDef.builder(TextInputType.TEXT, "sid_text", Template.text("composite text"))
                                .build())
                .build();
        Map<String, Object> actual = fmt.mappings(def);

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertTrue(actual.containsKey("sid"));
        assertEquals("text", ((Map) actual.get("sid")).get("type"));
    }

    @Test
    public void testHeaders_singleColumn() {
        CompositeQuestionDef def = CompositeQuestionDef.builder()
                .setStableId("sid")
                .setPrompt(Template.text("composite prompt"))
                .setAddButtonTemplate(Template.text("button"))
                .setAdditionalItemTemplate(Template.text("additional"))
                .addChildrenQuestions(
                        TextQuestionDef.builder(TextInputType.TEXT, "sid_text", Template.text("composite text"))
                                .build(),
                        DateQuestionDef
                                .builder(DateRenderMode.TEXT, "sid_date", Template.text("composite date"))
                                .addFields(DateFieldType.YEAR)
                                .build())
                .build();
        List<String> actual = fmt.headers(def);

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("sid", actual.get(0));
    }

    @Test
    public void testCollect_noRows() {
        Map<String, String> actual = fmt.collect(buildQuestion(), buildAnswer());

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("", actual.get("sid"));
    }

    @Test
    public void testCollect_eachElementIsSemicolonSeparated() {
        Map<String, String> actual = fmt.collect(buildQuestion(), buildAnswer(
                new AnswerRow(Arrays.asList(
                        new TextAnswer(10L, "sid_text", "aaa", "foobar"),
                        new DateAnswer(11L, "sid_date", "bbb", 2018, null, null)))));

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertTrue(actual.containsKey("sid"));
        assertEquals("foobar;2018", actual.get("sid"));
    }

    @Test
    public void testCollect_delimiterIsKeptIfNoValuesInRow() {
        Map<String, String> actual = fmt.collect(buildQuestion(), buildAnswer(
                new AnswerRow(Arrays.asList(
                        new TextAnswer(10L, "sid_text", "aaa", null),
                        new DateAnswer(11L, "sid_date", "bbb", null, null, null)))));

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertTrue(actual.containsKey("sid"));
        assertEquals(";", actual.get("sid"));
    }

    @Test
    public void testCollect_rowElementsAreSortedByDefinitionOrder() {
        Map<String, String> actual = fmt.collect(buildQuestion(), buildAnswer(
                new AnswerRow(Arrays.asList(
                        new DateAnswer(11L, "sid_date", "bbb", 2018, null, null),
                        new TextAnswer(10L, "sid_text", "aaa", "foobar")))));

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertTrue(actual.containsKey("sid"));
        assertEquals("foobar;2018", actual.get("sid"));
    }

    @Test
    public void testCollect_eachRowIsPipeSeparated() {
        Map<String, String> actual = fmt.collect(buildQuestion(), buildAnswer(
                new AnswerRow(Arrays.asList(
                        new TextAnswer(10L, "sid_text", "aaa", "foobar"),
                        new DateAnswer(11L, "sid_date", "bbb", 2018, null, null))),
                new AnswerRow(Arrays.asList(
                        new TextAnswer(12L, "sid_text", "ccc", "john smith"),
                        new DateAnswer(13L, "sid_date", "ddd", 1988, null, null)))));

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("foobar;2018|john smith;1988", actual.get("sid"));
    }

    @Test
    public void testCollect_missingValues() {
        Map<String, String> actual = fmt.collect(buildQuestion(), buildAnswer(
                new AnswerRow(Arrays.asList(
                        new TextAnswer(10L, "sid_text", "aaa", "foobar"),
                        new DateAnswer(11L, "sid_date", "bbb", 2018, null, null))),
                new AnswerRow(Arrays.asList(
                        new TextAnswer(12L, "sid_text", "ccc", null),
                        new DateAnswer(13L, "sid_date", "ddd", null, null, null))),
                new AnswerRow(Arrays.asList(
                        new TextAnswer(14L, "sid_text", "ccc", "baz"),
                        new DateAnswer(15L, "sid_date", "ddd", null, null, null))),
                new AnswerRow(Arrays.asList(
                        new TextAnswer(16L, "sid_text", "ccc", null),
                        new DateAnswer(17L, "sid_date", "ddd", 1988, null, null)))));

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("foobar;2018|;|baz;|;1988", actual.get("sid"));
    }

    @Test
    public void testCollect_textAndPicklist() {
        CompositeQuestionDef question = buildQuestion();
        question.getChildren().clear();
        question.getChildren().add(
                TextQuestionDef.builder(TextInputType.TEXT, "sid_text", Template.text("composite text")).build());
        question.getChildren().add(
                PicklistQuestionDef.buildMultiSelect(PicklistRenderMode.LIST, "sid_picklist", Template.text("composite picklist"))
                        .addOption(new PicklistOptionDef("op1", Template.text("option 1")))
                        .addOption(new PicklistOptionDef("op2", Template.text("option 2")))
                        .build());

        CompositeAnswer ans = new CompositeAnswer(1L, "sid", "abc");
        ans.setValue(Arrays.asList(
                new AnswerRow(Arrays.asList(
                        new TextAnswer(10L, "sid_text", "aaa", "foobar"),
                        new PicklistAnswer(11L, "sid_picklist", "bbb", Arrays.asList(new SelectedPicklistOption("op1"))))),
                new AnswerRow(Arrays.asList(
                        new TextAnswer(12L, "sid_text", "ccc", "foo, bar, and baz"),
                        new PicklistAnswer(13L, "sid_picklist", "bbb", Arrays.asList(
                                new SelectedPicklistOption("op2"), new SelectedPicklistOption("op1")))))));

        Map<String, String> actual = fmt.collect(question, ans);

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("foobar;op1|foo, bar, and baz;op2,op1", actual.get("sid"));
    }

    private CompositeQuestionDef buildQuestion() {
        return CompositeQuestionDef.builder()
                .setStableId("sid")
                .setPrompt(Template.text("composite prompt"))
                .setAllowMultiple(true)
                .setAddButtonTemplate(Template.text("button"))
                .setAdditionalItemTemplate(Template.text("additional"))
                .addChildrenQuestions(
                        TextQuestionDef.builder(TextInputType.TEXT, "sid_text", Template.text("composite text")).build(),
                        DateQuestionDef
                                .builder(DateRenderMode.TEXT, "sid_date", Template.text("composite date"))
                                .addFields(DateFieldType.YEAR)
                                .build())
                .build();
    }

    private CompositeAnswer buildAnswer(AnswerRow... rows) {
        CompositeAnswer ans = new CompositeAnswer(1L, "sid", "abc");
        ans.setValue(Arrays.asList(rows));
        return ans;
    }
}
