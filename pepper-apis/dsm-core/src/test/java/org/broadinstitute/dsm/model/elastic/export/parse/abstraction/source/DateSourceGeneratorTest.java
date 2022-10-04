
package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.source;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class DateSourceGeneratorTest {

    @Test
    public void simpleDateToMap() {
        var generator = new DateSourceGenerator();
        var actual = generator.toMap("Date PX", "2020-01-01");
        var expected = Map.of("datePx", "2020-01-01");
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void complexDateToMap() {
        var generator = new DateSourceGenerator();
        var actual = generator.toMap("Date PX", "{\"dateString\": \"2020-01-01\", \"est\": \"true\"}");
        var expected = Map.of("datePx", Map.of("dateString", "2020-01-01", "est", "true"));
        Assert.assertEquals(expected, actual);
    }

}
