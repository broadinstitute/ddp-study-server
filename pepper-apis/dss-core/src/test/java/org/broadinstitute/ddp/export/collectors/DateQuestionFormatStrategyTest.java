package org.broadinstitute.ddp.export.collectors;

import static org.broadinstitute.ddp.model.activity.types.DateFieldType.DAY;
import static org.broadinstitute.ddp.model.activity.types.DateFieldType.MONTH;
import static org.broadinstitute.ddp.model.activity.types.DateFieldType.YEAR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.CompleteRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.junit.Before;
import org.junit.Test;

public class DateQuestionFormatStrategyTest {

    private DateQuestionFormatStrategy fmt;

    @Before
    public void setup() {
        fmt = new DateQuestionFormatStrategy();
    }

    @Test
    public void testMappings_singleColumn_whenRequiredAndAllFieldsDefined() {
        DateQuestionDef def = DateQuestionDef.builder(DateRenderMode.TEXT, "sid", Template.text(""))
                .addFields(MONTH, DAY, YEAR)
                .addValidation(new RequiredRuleDef(null))
                .build();
        Map<String, Object> actual = fmt.mappings(def);

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertTrue(actual.containsKey("sid"));
        assertEquals("date", ((Map) actual.get("sid")).get("type"));
    }

    @Test
    public void testMappings_singleColumn_whenCompleteRuleAndAllFieldsDefined() {
        DateQuestionDef def = DateQuestionDef.builder(DateRenderMode.TEXT, "sid", Template.text(""))
                .addFields(MONTH, DAY, YEAR)
                .addValidation(new CompleteRuleDef(null))
                .build();
        Map<String, Object> actual = fmt.mappings(def);

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertTrue(actual.containsKey("sid"));
        assertEquals("date", ((Map) actual.get("sid")).get("type"));
    }

    @Test
    public void testMappings_propertyPerField() {
        DateQuestionDef def = DateQuestionDef.builder(DateRenderMode.TEXT, "sid", Template.text(""))
                .addFields(MONTH, YEAR)
                .build();
        Map<String, Object> actual = fmt.mappings(def);

        assertNotNull(actual);
        assertEquals(2, actual.size());
        assertTrue(actual.containsKey("sid_MONTH"));
        assertEquals("integer", ((Map) actual.get("sid_MONTH")).get("type"));
        assertTrue(actual.containsKey("sid_YEAR"));
        assertEquals("integer", ((Map) actual.get("sid_YEAR")).get("type"));
    }

    @Test
    public void testMappings_withoutFieldName_whenOnlyOneFieldDefined() {
        DateQuestionDef def = DateQuestionDef.builder(DateRenderMode.TEXT, "sid", Template.text(""))
                .addFields(YEAR)
                .build();
        Map<String, Object> actual = fmt.mappings(def);

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertTrue(actual.containsKey("sid"));
        assertEquals("integer", ((Map) actual.get("sid")).get("type"));
    }

    @Test
    public void testHeaders_singleColumn_whenRequiredAndAllFieldsDefined() {
        DateQuestionDef def = DateQuestionDef.builder(DateRenderMode.TEXT, "sid", Template.text(""))
                .addFields(MONTH, DAY, YEAR)
                .addValidation(new RequiredRuleDef(null))
                .build();
        List<String> actual = fmt.headers(def);

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("sid", actual.get(0));
    }

    @Test
    public void testHeaders_singleColumn_whenCompleteRuleAndAllFieldsDefined() {
        DateQuestionDef def = DateQuestionDef.builder(DateRenderMode.TEXT, "sid", Template.text(""))
                .addFields(MONTH, DAY, YEAR)
                .addValidation(new CompleteRuleDef(null))
                .build();
        List<String> actual = fmt.headers(def);

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("sid", actual.get(0));
    }

    @Test
    public void testHeaders_columnPerFieldInDefinitionOrder() {
        DateQuestionDef def = DateQuestionDef.builder(DateRenderMode.TEXT, "sid", Template.text(""))
                .addFields(MONTH, YEAR)
                .build();
        List<String> actual = fmt.headers(def);

        assertNotNull(actual);
        assertEquals(2, actual.size());
        assertEquals("sid_MONTH", actual.get(0));
        assertEquals("sid_YEAR", actual.get(1));
    }

    @Test
    public void testHeaders_withoutFieldName_whenOnlyOneFieldDefined() {
        DateQuestionDef def = DateQuestionDef.builder(DateRenderMode.TEXT, "sid", Template.text(""))
                .addFields(YEAR)
                .build();
        List<String> actual = fmt.headers(def);

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("sid", actual.get(0));
    }

    @Test
    public void testCollect_singleColumn_americanDateFormat_whenRequiredAndAllFields() {
        DateQuestionDef question = buildQuestion(YEAR, MONTH, DAY);
        question.getValidations().add(new RequiredRuleDef(null));
        Map<String, String> actual = fmt.collect(question, buildAnswer(new DateValue(2018, 3, 9)));

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertTrue(actual.containsKey("sid"));
        assertEquals("03/09/2018", actual.get("sid"));
    }

    @Test
    public void testCollect_singleColumn_americanDateFormat_whenCompleteRuleAndAllFields() {
        DateQuestionDef question = buildQuestion(YEAR, MONTH, DAY);
        question.getValidations().add(new CompleteRuleDef(null));
        Map<String, String> actual = fmt.collect(question, buildAnswer(new DateValue(2018, 3, 9)));

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertTrue(actual.containsKey("sid"));
        assertEquals("03/09/2018", actual.get("sid"));
    }

    @Test
    public void testCollect_columnPerField() {
        Map<String, String> actual = fmt.collect(buildQuestion(DAY, MONTH, YEAR), buildAnswer(new DateValue(2018, 3, 9)));

        assertNotNull(actual);
        assertEquals(3, actual.size());
        assertEquals("09", actual.get("sid_DAY"));
        assertEquals("03", actual.get("sid_MONTH"));
        assertEquals("2018", actual.get("sid_YEAR"));
    }

    @Test
    public void testCollect_missingValues() {
        Map<String, String> actual = fmt.collect(buildQuestion(DAY, MONTH, YEAR), buildAnswer(new DateValue(2018, null, null)));

        assertNotNull(actual);
        assertEquals(3, actual.size());
        assertEquals(null, actual.get("sid_DAY"));
        assertEquals(null, actual.get("sid_MONTH"));
        assertEquals("2018", actual.get("sid_YEAR"));
    }

    @Test
    public void testCollect_singleDefinedField() {
        Map<String, String> actual = fmt.collect(buildQuestion(MONTH), buildAnswer(new DateValue(2018, 3, 14)));

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("03", actual.get("sid"));
    }

    private DateQuestionDef buildQuestion(DateFieldType... fields) {
        return DateQuestionDef.builder(DateRenderMode.TEXT, "sid", Template.text(""))
                .addFields(fields)
                .build();
    }

    private DateAnswer buildAnswer(DateValue value) {
        return new DateAnswer(1L, "sid", "abc", value);
    }
}
