package org.broadinstitute.dsm.model.elastic.export.parse;

import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

public class MedicalRecordAbstractionFieldTypeParserTest {

    private MedicalRecordAbstractionFieldTypeParser parser;

    @Before
    public void setUp() {
        parser = new MedicalRecordAbstractionFieldTypeParser(new TypeParser());
    }

    @Test
    public void buildMultiOptionsMapping() {

        parser.setType("multi_options");

        var expected = new HashMap<String, Object>(Map.of(
                "type", "nested",
                "properties", new HashMap<String, Object>(Map.of(
                        "other", TypeParser.TEXT_KEYWORD_MAPPING,
                        "values", new HashMap<String, Object>(Map.of(
                                "type", "nested",
                                "properties", new HashMap<String, Object>(Map.of(
                                        "value", TypeParser.TEXT_KEYWORD_MAPPING))))))));

        var actual = parser.parse("MET_SITES_EVERY");

        assertEquals(expected, actual);
    }

    @Test
    public void buildDateMapping() {

        parser.setType("date");

        var expected = new HashMap<String, Object>(Map.of(
                "properties", new HashMap<>(Map.of(
                        "dateString", TypeParser.DATE_MAPPING,
                        "est", TypeParser.BOOLEAN_MAPPING))));

        var actual = parser.parse("DX Date");

        assertEquals(expected, actual);

    }


    @Test
    public void buildMultiTypeArrayMapping() {

        var possibleValues = "[{\"value\":\"Other Cancer\",\"type\":\"text\"},{\"value\":\"Site Other Cancer\",\"type\":\"text\"},{\"value\":\"Date Other Cancer\",\"type\":\"date\"}]";

        parser.setType("multi_type_array");
        parser.setPossibleValues(possibleValues);

        var expectedInnerMapping = new LinkedHashMap<String, Object>();

        expectedInnerMapping.put("otherCancer", TypeParser.TEXT_KEYWORD_MAPPING);
        expectedInnerMapping.put("siteOtherCancer", TypeParser.TEXT_KEYWORD_MAPPING);
        expectedInnerMapping.put("dateOtherCancer", new HashMap<>(Map.of("properties", Map.of("dateString", TypeParser.DATE_MAPPING,"est", TypeParser.BOOLEAN_MAPPING))));

        var expected = new HashMap<String, Object>(Map.of("type", "nested","properties", expectedInnerMapping));

        var actual = parser.parse("Other Cancers");

        assertEquals(expected, actual);

    }


    @Test
    public void buildComplexMultiTypeArrayMapping() {

        var possibleValues = "[{\"value\":\"Type of Procedure\",\"type\":\"button_select\",\"values\":[{\"value\":\"Biopsy\"},{\"value\":\"Prostatectomy\"},{\"value\":\"Other\"}]},{\"value\":\"TNM Classification\",\"type\":\"button_select\",\"values\":[{\"value\":\"c\"},{\"value\":\"p\"},{\"value\":\"yp\"},{\"value\":\"Other\"}]}]";

        parser.setType("multi_type_array");
        parser.setPossibleValues(possibleValues);

        var expectedInnerMapping = new LinkedHashMap<String, Object>();

        expectedInnerMapping.put("typeOfProcedure", TypeParser.TEXT_KEYWORD_MAPPING);
        expectedInnerMapping.put("tnmClassification", TypeParser.TEXT_KEYWORD_MAPPING);

        var expected = new HashMap<String, Object>(Map.of("type", "nested","properties", expectedInnerMapping));

        var actual = parser.parse("TNM");

        assertEquals(expected, actual);

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