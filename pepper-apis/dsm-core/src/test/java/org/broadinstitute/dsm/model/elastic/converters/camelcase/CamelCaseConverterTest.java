package org.broadinstitute.dsm.model.elastic.converters.camelcase;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CamelCaseConverterTest {

    @Test
    public void isCamelCase() {
        String fieldName = "tissueType";
        assertTrue(CamelCaseConverter.of().isCamelCase(fieldName));
    }
}
