package org.broadinstitute.dsm.model.elastic.export;

import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;
import org.junit.Assert;
import org.junit.Test;

public class ValueParserTest {

    private static final ValueParser parser = new ValueParser();

    @Test
    public void testParsingOfDecimalPointZeroToLong() {
        Long longValue = (Long)parser.forNumeric("13.0");
        Assert.assertEquals(13L, longValue.longValue());
    }

    @Test
    public void testParsingOfDecimalToLong() {
        String decimal = "13.353535";
        try {
            parser.forNumeric(decimal);
            Assert.fail("Should not be able to parse " + decimal + " into a long");
        } catch (DsmInternalError e) {
            Assert.assertTrue(e.getMessage().contains(decimal));
        }
    }

    @Test
    public void testParsingOfIntToLong() {
        Long longValue = (Long)parser.forNumeric("200");
        Assert.assertEquals(200L, longValue.longValue());
    }

    @Test
    public void testParsingEmptyToNull() {
        Assert.assertNull(parser.forNumeric(null));
        Assert.assertNull(parser.forNumeric(""));
    }
}
