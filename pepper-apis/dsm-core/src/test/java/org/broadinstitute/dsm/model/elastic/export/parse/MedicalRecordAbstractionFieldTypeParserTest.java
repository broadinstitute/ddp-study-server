package org.broadinstitute.dsm.model.elastic.export.parse;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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