package org.broadinstitute.dsm.model.elastic.converters;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;
import org.junit.Test;

public class DefaultConverterTest {

    @Test
    public void convert() {

        DefaultConverter defaultConverter = new DefaultConverter();
        defaultConverter.fieldName = "random_field";
        defaultConverter.fieldValue = true;

        Map<String, Object> actualMap = defaultConverter.convert();

        Map<String, Boolean> expectedMap = Map.of("randomField", true);

        assertEquals(expectedMap, actualMap);
    }

    @Test
    public void convertNA() {

        DefaultConverter defaultConverter = new DefaultConverter();
        defaultConverter.fieldName = "random_date_field";
        defaultConverter.fieldValue = "N/A";

        Map<String, Object> actualMap = defaultConverter.convert();

        Map<String, String> expectedMap = Map.of("randomDateField", ValueParser.N_A_SYMBOLIC_DATE);

        assertEquals(expectedMap, actualMap);

    }
}
