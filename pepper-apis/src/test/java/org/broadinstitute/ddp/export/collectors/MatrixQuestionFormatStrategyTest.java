package org.broadinstitute.ddp.export.collectors;

import org.broadinstitute.ddp.model.activity.definition.question.MatrixGroupDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixRowDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.answer.MatrixAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedMatrixCell;
import org.broadinstitute.ddp.model.activity.types.MatrixSelectMode;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class MatrixQuestionFormatStrategyTest {

    private MatrixQuestionFormatStrategy fmt;

    @Before
    public void setup() {
        fmt = new MatrixQuestionFormatStrategy();
    }

    @Test
    public void testMappings_Multiple() {
        MatrixQuestionDef def = MatrixQuestionDef.builder(MatrixSelectMode.MULTIPLE, "sid", Template.text(""))
                .addOptions(List.of(
                        new MatrixOptionDef("OPT_1", Template.text("option 1"), "DEFAULT"),
                        new MatrixOptionDef("OPT_2", Template.text("option 2"), "GROUP")))
                .addRows(List.of(
                        new MatrixRowDef("ROW_1", Template.text("row 1")),
                        new MatrixRowDef("ROW_2", Template.text("row 2"))))
                .addGroups(List.of(
                        new MatrixGroupDef("DEFAULT", null),
                        new MatrixGroupDef("GROUP", Template.text("group 1"))))
                .build();

        Map<String, Object> mappings = fmt.mappings(def);

        assertNotNull(mappings);
        assertEquals(1, mappings.size());
        assertTrue(mappings.containsKey("sid"));
        assertEquals("text", ((Map) mappings.get("sid")).get("type"));

        List<String> headers = fmt.headers(def);

        assertNotNull(headers);
        assertEquals(1, headers.size());
        assertEquals("sid", headers.get(0));
    }

    @Test
    public void testMappings_Single() {
        MatrixQuestionDef def = MatrixQuestionDef.builder(MatrixSelectMode.SINGLE, "sid", Template.text(""))
                .addOptions(List.of(
                        new MatrixOptionDef("OPT_1", Template.text("option 1"), "DEFAULT"),
                        new MatrixOptionDef("OPT_2", Template.text("option 2"), "GROUP")))
                .addRows(List.of(
                        new MatrixRowDef("ROW_1", Template.text("row 1")),
                        new MatrixRowDef("ROW_2", Template.text("row 2"))))
                .addGroups(List.of(
                        new MatrixGroupDef("DEFAULT", null),
                        new MatrixGroupDef("GROUP", Template.text("group 1"))))
                .build();

        Map<String, Object> mappings = fmt.mappings(def);

        assertNotNull(mappings);
        assertEquals(1, mappings.size());
        assertTrue(mappings.containsKey("sid"));
        assertEquals("keyword", ((Map) mappings.get("sid")).get("type"));

        List<String> headers = fmt.headers(def);

        assertNotNull(headers);
        assertEquals(1, headers.size());
        assertEquals("sid", headers.get(0));
    }

    @Test
    public void testQuestionDef_withOptionsRowsGroups() {
        MatrixQuestionDef def = MatrixQuestionDef.builder(MatrixSelectMode.SINGLE, "sid", Template.text(""))
                .addOptions(List.of(
                        new MatrixOptionDef("OPT_1", Template.text("option 1"), "DEFAULT"),
                        new MatrixOptionDef("OPT_2", Template.text("option 2"), "GROUP")))
                .addRows(List.of(
                        new MatrixRowDef("ROW_1", Template.text("row 1")),
                        new MatrixRowDef("ROW_2", Template.text("row 2"))))
                .addGroups(List.of(
                        new MatrixGroupDef("DEFAULT", null),
                        new MatrixGroupDef("GROUP", Template.text("group 1"))))
                .build();

        Map<String, Object> actual = fmt.questionDef(def);
        assertNotNull(actual);
        assertEquals("sid", actual.get("stableId"));
        assertEquals("MATRIX", actual.get("questionType"));
        assertEquals(MatrixSelectMode.SINGLE, actual.get("selectMode"));

        assertTrue(actual.containsKey("options"));
        assertTrue(actual.containsKey("rows"));
        assertTrue(actual.containsKey("groups"));

        List<Map<String, Object>> options = (List<Map<String, Object>>) actual.get("options");
        List<Map<String, Object>> groups = (List<Map<String, Object>>) actual.get("groups");
        List<Map<String, Object>> rows = (List<Map<String, Object>>) actual.get("rows");

        assertEquals(2, options.size());
        assertEquals(2, rows.size());
        assertEquals(2, groups.size());

        assertEquals("OPT_1", options.get(0).get("optionStableId"));
        assertEquals("option 1", options.get(0).get("optionText"));
        assertEquals("OPT_2", options.get(1).get("optionStableId"));
        assertEquals("option 2", options.get(1).get("optionText"));
        assertEquals("ROW_1", rows.get(0).get("rowStableId"));
        assertEquals("row 1", rows.get(0).get("rowText"));
        assertEquals("ROW_2", rows.get(1).get("rowStableId"));
        assertEquals("row 2", rows.get(1).get("rowText"));
        assertEquals("DEFAULT", groups.get(0).get("groupStableId"));
        assertEquals("GROUP", groups.get(1).get("groupStableId"));
        assertEquals("group 1", groups.get(1).get("groupText"));
    }

    @Test
    public void testQuestionDef_withOptionsRows() {
        MatrixQuestionDef def = MatrixQuestionDef.builder(MatrixSelectMode.SINGLE, "sid", Template.text(""))
                .addOptions(List.of(
                        new MatrixOptionDef("OPT_1", Template.text("option 1"), "DEFAULT"),
                        new MatrixOptionDef("OPT_2", Template.text("option 2"), "GROUP")))
                .addRows(List.of(
                        new MatrixRowDef("ROW_1", Template.text("row 1")),
                        new MatrixRowDef("ROW_2", Template.text("row 2"))))
                .addGroups(List.of(
                        new MatrixGroupDef("DEFAULT", null),
                        new MatrixGroupDef("GROUP", null)))
                .build();

        Map<String, Object> actual = fmt.questionDef(def);
        assertNotNull(actual);
        assertEquals("sid", actual.get("stableId"));
        assertEquals("MATRIX", actual.get("questionType"));
        assertEquals(MatrixSelectMode.SINGLE, actual.get("selectMode"));

        assertTrue(actual.containsKey("options"));
        assertTrue(actual.containsKey("rows"));
        assertTrue(actual.containsKey("groups"));

        List<Map<String, Object>> options = (List<Map<String, Object>>) actual.get("options");
        List<Map<String, Object>> rows = (List<Map<String, Object>>) actual.get("rows");
        List<Object> groups = (List<Object>) actual.get("groups");

        assertEquals(2, options.size());
        assertEquals(2, rows.size());
        assertEquals(2, groups.size());

        assertEquals("OPT_1", options.get(0).get("optionStableId"));
        assertEquals("option 1", options.get(0).get("optionText"));
        assertEquals("OPT_2", options.get(1).get("optionStableId"));
        assertEquals("option 2", options.get(1).get("optionText"));
        assertEquals("ROW_1", rows.get(0).get("rowStableId"));
        assertEquals("row 1", rows.get(0).get("rowText"));
        assertEquals("ROW_2", rows.get(1).get("rowStableId"));
        assertEquals("row 2", rows.get(1).get("rowText"));
    }

    @Test
    public void testCollect_answerWithNoSelections() {
        MatrixQuestionDef questionDef = MatrixQuestionDef.builder(MatrixSelectMode.SINGLE, "sid", Template.text(""))
                .addOptions(List.of(
                        new MatrixOptionDef("OPT_1", Template.text("option 1"), "DEFAULT"),
                        new MatrixOptionDef("OPT_2", Template.text("option 2"), "GROUP")))
                .addRows(List.of(
                        new MatrixRowDef("ROW_1", Template.text("row 1")),
                        new MatrixRowDef("ROW_2", Template.text("row 2"))))
                .addGroups(List.of(
                        new MatrixGroupDef("DEFAULT", null),
                        new MatrixGroupDef("GROUP", Template.text("group 1"))))
                .build();

        Map<String, String> actual = fmt.collect(questionDef, buildAnswer());

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("", actual.get("sid"));
    }

    @Test
    public void testCollect_selectedOptionsSortedInDefinitionOrder() {
        MatrixQuestionDef questionDef = MatrixQuestionDef.builder(MatrixSelectMode.SINGLE, "sid", Template.text(""))
                .addOptions(List.of(
                        new MatrixOptionDef("OPT_1", Template.text("option 1"), "DEFAULT"),
                        new MatrixOptionDef("OPT_2", Template.text("option 2"), "GROUP")))
                .addRows(List.of(
                        new MatrixRowDef("ROW_1", Template.text("row 1")),
                        new MatrixRowDef("ROW_2", Template.text("row 2"))))
                .addGroups(List.of(
                        new MatrixGroupDef("DEFAULT", null),
                        new MatrixGroupDef("GROUP", Template.text("group 1"))))
                .build();

        Map<String, String> actual = fmt.collect(questionDef, buildAnswer(
                new SelectedMatrixCell("ROW_1", "OPT_1", "DEFAULT"),
                new SelectedMatrixCell("ROW_2", "OPT_1", "DEFAULT")));

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertTrue(actual.containsKey("sid"));
        assertEquals("ROW_1:OPT_1,ROW_2:OPT_1", actual.get("sid"));
    }

    private MatrixAnswer buildAnswer(SelectedMatrixCell... selected) {
        return new MatrixAnswer(1L, "sid", "abc", Arrays.asList(selected));
    }
}
