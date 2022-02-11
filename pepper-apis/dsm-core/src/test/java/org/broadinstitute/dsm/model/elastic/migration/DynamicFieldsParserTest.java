package org.broadinstitute.dsm.model.elastic.migration;

import static org.junit.Assert.*;

import java.util.Map;

import org.broadinstitute.dsm.model.elastic.export.generate.MappingGenerator;
import org.broadinstitute.dsm.model.elastic.export.parse.DynamicFieldsParser;
import org.broadinstitute.dsm.model.elastic.export.parse.TypeParser;
import org.junit.Test;

public class DynamicFieldsParserTest {

    @Test
    public void parse() {
        String possibleValuesJson = "[{\"value\":\"CONSENT.completedAt\",\"type\":\"DATE\"}]";
        String displayType = "ACTIVITY_STAFF";
        DynamicFieldsParser dynamicFieldsParser = new DynamicFieldsParser();
        dynamicFieldsParser.setDisplayType(displayType);
        dynamicFieldsParser.setPossibleValuesJson(possibleValuesJson);
        dynamicFieldsParser.setParser(new TypeParser());
        Map<String, Object> mapping = (Map<String, Object>) dynamicFieldsParser.parse(displayType);
        Object date = mapping.get(MappingGenerator.TYPE);
        assertEquals(TypeParser.DATE, date);
    }

}