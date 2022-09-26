
package org.broadinstitute.dsm.model.elastic.export.parse;

import static org.broadinstitute.dsm.model.elastic.export.generate.MappingGenerator.NESTED;
import static org.broadinstitute.dsm.model.elastic.export.generate.MappingGenerator.PROPERTIES;
import static org.broadinstitute.dsm.model.elastic.export.parse.MedicalRecordAbstractionFieldTypeParser.OTHER;
import static org.broadinstitute.dsm.model.elastic.export.parse.MedicalRecordAbstractionFieldTypeParser.VALUES;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.model.elastic.export.generate.MappingGenerator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class MedicalRecordAbstractionFieldTypeParserTest {

    private MedicalRecordAbstractionFieldTypeParser parser;

    @Before
    public void setUp() {
        parser = new MedicalRecordAbstractionFieldTypeParser(new TypeParser());
    }

    @Test
    public void parseDate() {
        parser.setType("date");
        var expected = new HashMap<String, Object>(Map.of(
                "properties", new HashMap<>(Map.of(
                        "dateString", TypeParser.DATE_MAPPING,
                        "est", TypeParser.BOOLEAN_MAPPING))));
        var actual = parser.parse("DX Date");
        assertEquals(expected, actual);
    }

    @Test
    public void parseButtonSelect() {
        var fieldName = "Prostatic Adenocarcinoma";
        parser.setType(MedicalRecordAbstractionFieldType.BUTTON_SELECT.asString());
        var actual = parser.parse(fieldName);
        var expected = TypeParser.TEXT_KEYWORD_MAPPING;
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void parseNumber() {
        var fieldName = "DX percent ER";
        parser.setType(MedicalRecordAbstractionFieldType.NUMBER.asString());
        var expectedInnerMapping = TypeParser.LONG_MAPPING;
        var actual = parser.parse(fieldName);
        Assert.assertEquals(expectedInnerMapping, actual);
    }

    @Test
    public void parseMultiOptions() {
        parser.setType(MedicalRecordAbstractionFieldType.MULTI_OPTIONS.asString());
        Map<String, Object> expected = new HashMap<>(Map.of(
                MappingGenerator.TYPE, NESTED,
                PROPERTIES, new HashMap<>(Map.of(
                        OTHER, TypeParser.TEXT_KEYWORD_MAPPING,
                        VALUES, new HashMap<>(Map.of(
                                MappingGenerator.TYPE, NESTED,
                                PROPERTIES, new HashMap<>(Map.of(
                                        VALUES, TypeParser.TEXT_KEYWORD_MAPPING))))))));

        var actual = parser.parse("MET_SITES_EVERY");
        assertEquals(expected, actual);
    }

    // Will also pass for `table` data type as well
    @Test
    public void parseSimpleMultiTypeArray() {
        var possibleValues = "[{\"value\":\"PSA\",\"type\":\"number\"},{\"value\":\"Date of PSA\",\"type\":\"date\"}]";
        parser.setType(MedicalRecordAbstractionFieldType.MULTI_TYPE_ARRAY.asString());
        parser.setPossibleValues(possibleValues);
        var expectedInnerMapping = new LinkedHashMap<String, Object>();

        expectedInnerMapping.put("singleAnswer", TypeParser.TEXT_KEYWORD_MAPPING);
        expectedInnerMapping.put("psa", TypeParser.LONG_MAPPING);
        expectedInnerMapping.put("dateOfPsa", new HashMap<>(Map.of("properties", Map.of("dateString", TypeParser.DATE_MAPPING,
                "est", TypeParser.BOOLEAN_MAPPING))));

        var expected = new HashMap<String, Object>(Map.of(
                "type", "nested",
                "properties", expectedInnerMapping));

        var actual = parser.parse("PSA");

        Assert.assertEquals(expected, actual);
    }

    // Will also pass for `table` data type as well
    @Test
    public void parseTypicalMultiTypeArray() {

        var possibleValues = "[{\"value\":\"Other Cancer\",\"type\":\"text\"},{\"value\":\"Site Other Cancer\",\"type\":\"text\"},"
                + "{\"value\":\"Date Other Cancer\",\"type\":\"date\"}]";

        parser.setType("multi_type_array");
        parser.setPossibleValues(possibleValues);

        var expectedInnerMapping = new LinkedHashMap<String, Object>();

        expectedInnerMapping.put("singleAnswer", TypeParser.TEXT_KEYWORD_MAPPING);
        expectedInnerMapping.put("otherCancer", TypeParser.TEXT_KEYWORD_MAPPING);
        expectedInnerMapping.put("siteOtherCancer", TypeParser.TEXT_KEYWORD_MAPPING);
        expectedInnerMapping.put("dateOtherCancer", new HashMap<>(Map.of("properties", Map.of("dateString", TypeParser.DATE_MAPPING,
                "est", TypeParser.BOOLEAN_MAPPING))));

        var expected = new HashMap<String, Object>(Map.of("type", "nested",
                "properties", expectedInnerMapping));

        var actual = parser.parse("Other Cancers");

        assertEquals(expected, actual);
    }

    // Will also pass for `tables` data type as well
    @Test
    public void parseComplexMultiTypeArray() {

        var possibleValues = "[{\"value\":\"Type of Procedure\",\"type\":\"button_select\",\"values\":[{\"value\":\"Biopsy\"},"
                + "{\"value\":\"Prostatectomy\"},{\"value\":\"Other\"}]},{\"value\":\"TNM Classification\",\"type\":\"button_select\","
                + "\"values\":[{\"value\":\"c\"},{\"value\":\"p\"},{\"value\":\"yp\"},{\"value\":\"Other\"}]}]";

        parser.setType("multi_type_array");
        parser.setPossibleValues(possibleValues);

        var expectedInnerMapping = new LinkedHashMap<String, Object>();

        expectedInnerMapping.put("singleAnswer", TypeParser.TEXT_KEYWORD_MAPPING);
        expectedInnerMapping.put("typeOfProcedure", TypeParser.TEXT_KEYWORD_MAPPING);
        expectedInnerMapping.put("tnmClassification", TypeParser.TEXT_KEYWORD_MAPPING);

        var expected = new HashMap<String, Object>(Map.of("type", "nested",
                "properties", expectedInnerMapping));

        var actual = parser.parse("TNM");

        assertEquals(expected, actual);

    }

    @Test
    public void parseText() {
        var fieldName = "Number Positive LN";
        parser.setType(MedicalRecordAbstractionFieldType.TEXT.asString());
        var actual = parser.parse(fieldName);
        var expected = TypeParser.TEXT_KEYWORD_MAPPING;
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void parseTextArea() {
        var fieldName = "Patient notes";
        parser.setType(MedicalRecordAbstractionFieldType.TEXT_AREA.asString());
        var actual = parser.parse(fieldName);
        var expected = TypeParser.TEXT_KEYWORD_MAPPING;
        Assert.assertEquals(expected, actual);
    }


    @Test
    public void setPossibleValues() {

        var possibleValues = "[{\"value\":\"PSA\",\"type\":\"number\"},{\"value\":\"Date of PSA\",\"type\":\"date\"}]";

        parser.setType("multi_type_array");
        parser.setPossibleValues(possibleValues);

        var expected = new ArrayList<Map<String, Object>>(List.of(
                new HashMap<>(Map.of(
                        "value", "PSA",
                        "type", "number")),
                new HashMap<>(Map.of(
                        "value", "Date of PSA",
                        "type", "date"))
        ));

        var actual = parser.getPossibleValues();

        assertEquals(expected, actual);
    }

}
